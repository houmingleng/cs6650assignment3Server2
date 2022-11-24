package Consumer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import Model.LiftRide;

import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;

public class SkiersConsumer {
    private final static String QUEUE_NAME = "rpc_queue";
    private final static Integer THREADS = 500;
    private final static String EXCHANGE_NAME = "liftRide";
    private static JedisPool pool = null;
    private final static Integer REDIS_PORT = 6379;
    //private final static String REDIS_HOST = "35.92.115.63";
    private final static String REDIS_HOST ="127.0.0.1";
    public static void main(String[] args) throws Exception{
        ConcurrentHashMap<String, CopyOnWriteArrayList<LiftRide>> map = new ConcurrentHashMap<>();
        ConnectionFactory factory = new ConnectionFactory();
        Gson gson = new Gson();
        factory.setHost("localhost");
        factory.setUsername("guest");
        factory.setPassword("guest");
        Connection connection = factory.newConnection();

        pool = new JedisPool(REDIS_HOST, REDIS_PORT);
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(100);
        pool.setConfig(config);

        Runnable runnable = () -> {
            Channel channel;
            try{
                channel = connection.createChannel();
                channel.queueDeclare(QUEUE_NAME, false, false, false, null);
                channel.exchangeDeclare(EXCHANGE_NAME, "fanout");
                String queueName = channel.queueDeclare().getQueue();
                channel.queueBind("queue2", EXCHANGE_NAME, "");
                //channel.queueDeclare(QUEUE_NAME, false, false, false, null);
                // channel.queuePurge(QUEUE_NAME);
                DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                    String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                    LiftRide liftRide = gson.fromJson(message, LiftRide.class);

                    System.out.println(" [x] Received '" + liftRide.toString() + "'");
                    try{
                        addHashMap(liftRide);
                    } finally {
                        System.out.println("[x] Done" + liftRide.toString());
                        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), true);
                    }
                };
                channel.basicConsume("queue2", false, deliverCallback, consumerTag -> { });
            } catch (IOException e) {
                e.printStackTrace();
            }
        };

        for (int i = 0; i < THREADS; i++) {
            Thread thread = new Thread(runnable);
            thread.start();
        }
    }

    private static void addHashMap(LiftRide liftRide) {
        Map<String, String> newMap = new HashMap<>();
        String potentialKey = "sId "+ liftRide.getDayID() + "  dayId "+liftRide.getDayID() + "  seasonId "+liftRide.getSeasonID()+ " liftResortIDd "+ liftRide.getResortID()+ "  liftId "+liftRide.getLiftID()+ "  liftTime "+liftRide.getTime();
        newMap.put("skierId", liftRide.getSkierID());
        newMap.put("resortId", liftRide.getResortID());
        newMap.put("seasonId", liftRide.getSeasonID());
        newMap.put("dayId", liftRide.getDayID());
        newMap.put("liftId", liftRide.getLiftID());
        newMap.put("time", liftRide.getTime());
        Jedis jedis = pool.getResource();
        try{
            jedis.hmset(potentialKey, newMap);
            System.out.println("done");
        }catch (JedisException e){
            if (null != jedis) {
                pool.returnBrokenResource(jedis);
                jedis = null;
            }
        } finally {
            if (null != jedis)
                pool.returnResource(jedis);
        }
    }


}

