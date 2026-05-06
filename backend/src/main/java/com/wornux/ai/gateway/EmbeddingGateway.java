package com.wornux.ai.gateway;

import java.util.List;

public interface EmbeddingGateway {
  List<Double> embed(String text);
}
