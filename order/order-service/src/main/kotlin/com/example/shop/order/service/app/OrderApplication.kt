package com.example.shop.order.service.app

import akka.cluster.sharding.typed.javadsl.ClusterSharding

class OrderApplication(val shard: ClusterSharding)