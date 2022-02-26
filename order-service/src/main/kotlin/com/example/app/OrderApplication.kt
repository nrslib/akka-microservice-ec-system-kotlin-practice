package com.example.app

import akka.cluster.sharding.typed.javadsl.ClusterSharding

class OrderApplication(val shard: ClusterSharding)