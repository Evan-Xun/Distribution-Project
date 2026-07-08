package distproject.message;

import java.io.Serializable;

public enum MessageType implements Serializable {
    REGISTER_TABLE,
    TABLE_ASSIGNED,
    REQUEST_MENU,
    MENU_DATA,
    ADD_TO_SHARED_CART,
    REMOVE_FROM_SHARED_CART,
    CART_UPDATED,
    SUBMIT_ORDER,
    ORDER_RECEIVED,
    ORDER_STATUS_UPDATED,
    REGISTER_TAKEAWAY,
    ERROR
}
