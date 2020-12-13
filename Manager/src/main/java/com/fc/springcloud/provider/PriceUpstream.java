package com.fc.springcloud.provider;

import java.util.concurrent.BlockingQueue;

public interface PriceUpstream {
  // Push is a forever loop function without return, will add new price to the queue
  public void Push(BlockingQueue<Float> queue);
}
