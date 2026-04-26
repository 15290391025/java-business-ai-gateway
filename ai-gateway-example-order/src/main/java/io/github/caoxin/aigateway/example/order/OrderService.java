package io.github.caoxin.aigateway.example.order;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderService {

    private final Map<String, OrderDetailResult> orders = new ConcurrentHashMap<>();

    public OrderService() {
        orders.put("20260426001", new OrderDetailResult(
            "20260426001",
            "NOT_SHIPPED",
            "PAID",
            "WAITING_FOR_PICKUP",
            "上海市浦东新区示例路 100 号"
        ));
        orders.put("20260426002", new OrderDetailResult(
            "20260426002",
            "SHIPPED",
            "PAID",
            "IN_TRANSIT",
            "杭州市西湖区示例路 200 号"
        ));
    }

    public OrderDetailResult queryDetail(String orderId) {
        OrderDetailResult order = orders.get(orderId);
        if (order == null) {
            return new OrderDetailResult(orderId, "NOT_FOUND", "UNKNOWN", "UNKNOWN", "");
        }
        return order;
    }

    public CancelOrderResult cancel(String orderId, String reason) {
        OrderDetailResult previous = queryDetail(orderId);
        OrderDetailResult canceled = new OrderDetailResult(
            orderId,
            "CANCELED",
            previous.paymentStatus(),
            previous.logisticsStatus(),
            previous.address()
        );
        orders.put(orderId, canceled);
        return new CancelOrderResult(true, "订单已取消: " + reason, orderId, previous.status(), canceled.status());
    }
}

