package com.wornux.ai.routing;

public interface ConversationRouter {
  PedagogicalRoutingMode route(String message);
}
