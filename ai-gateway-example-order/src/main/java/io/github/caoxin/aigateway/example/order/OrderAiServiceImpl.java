package io.github.caoxin.aigateway.example.order;

import org.springframework.stereotype.Component;

@Component
public class OrderAiServiceImpl implements OrderAiService {

    private final OrderService orderService;

    public OrderAiServiceImpl(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public OrderDetailResult queryOrder(QueryOrderCommand command) {
        return orderService.queryDetail(command.orderId());
    }

    @Override
    public CancelOrderResult cancelOrder(CancelOrderCommand command) {
        OrderDetailResult order = orderService.queryDetail(command.orderId());
        if (!"NOT_SHIPPED".equals(order.status())) {
            return new CancelOrderResult(false, "订单已发货，不能取消", command.orderId(), order.status(), order.status());
        }
        return orderService.cancel(command.orderId(), command.reason());
    }
}

