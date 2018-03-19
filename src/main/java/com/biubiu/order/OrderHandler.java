package com.biubiu.order;

import com.google.common.collect.Maps;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author 张海彪
 * @create 2018-03-17 下午8:47
 */
@Component
public class OrderHandler {

    private Jedis jedis = new Jedis("127.0.0.1", 6379);

    private AtomicInteger currentOrderId = new AtomicInteger(0);

    private SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static final String KEYS = "orders";

    @Scheduled(cron = "0/1 * * ? * *")
    private void initData() throws ParseException {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND, 60);
        Map<String, Double> scores = Maps.newHashMap();
        for (int i = 0; i < 170; i++) {
            currentOrderId.getAndIncrement();
            scores.put(currentOrderId.toString(), (double) getMillis(calendar));
            jedis.zadd(KEYS, scores);
            System.out.println("当前订单ID：" + currentOrderId);
        }
    }

    @Scheduled(cron = "0/1 * * ? * *")
    private void consumer() throws ParseException {
        Calendar calendar = Calendar.getInstance();
        long score = getMillis(calendar);
        Set<String> orders = jedis.zrangeByScore(KEYS, 0, score);
        if (orders == null || orders.isEmpty() || orders.size() == 0) {
            System.out.println("==============暂时没有订单：时间：" + calendar.getTime());
        }
        for (String order : orders) {
            System.out.println("=============处理订单，订单ID：" + order);
            long result = jedis.zrem(KEYS, order);
            System.out.println("=============处理完毕，订单ID：" + order + ", 删除结果:" + (result == 1));
        }
    }

    private long getMillis(Calendar calendar) throws ParseException {
        String date = format.format(calendar.getTime());
        Date nowTime = format.parse(date);
        System.out.println("-------------当前时间：" + nowTime + "-------------毫秒数" + nowTime.getTime());
        return nowTime.getTime();
    }

}
