package com.fc.springcloud.provider.Impl;

import com.fc.springcloud.provider.PriceUpstream;
import java.util.List;
import java.util.concurrent.BlockingQueue;

// FakePriceUpstream just for test, so it will push a certain number of elements
public class FakePriceUpstream implements PriceUpstream {

  private List<Float> prices;
  FakePriceUpstream(List<Float> prices) {
    this.prices = prices;
  }

  @Override
  public void Push(BlockingQueue<Float> queue) {
    for (Float price: prices) {
      queue.add(price);
    }
    while (true){}
  }
}
