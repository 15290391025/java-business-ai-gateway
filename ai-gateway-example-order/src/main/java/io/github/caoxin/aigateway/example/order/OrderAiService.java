package io.github.caoxin.aigateway.example.order;

import io.github.caoxin.aigateway.annotation.AiConfirm;
import io.github.caoxin.aigateway.annotation.AiIntent;
import io.github.caoxin.aigateway.annotation.AiModule;
import io.github.caoxin.aigateway.annotation.AiPermission;
import io.github.caoxin.aigateway.annotation.RiskLevel;

@AiModule(
    name = "order",
    description = "订单模块，负责订单查询、取消订单和修改收货地址"
)
public interface OrderAiService {

    @AiIntent(
        name = "query_order",
        description = "根据订单号查询订单详情、订单状态、支付状态和物流状态",
        examples = {"帮我查一下订单 20260426001"}
    )
    @AiPermission("order:read")
    OrderDetailResult queryOrder(QueryOrderCommand command);

    @AiIntent(
        name = "cancel_order",
        description = "取消未发货订单。已发货订单不能取消。",
        examples = {"帮我取消订单 20260426001"}
    )
    @AiPermission("order:cancel")
    @AiConfirm(level = RiskLevel.HIGH, message = "取消订单是高风险操作，需要用户确认")
    CancelOrderResult cancelOrder(CancelOrderCommand command);
}

