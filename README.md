[![Build Status](https://travis-ci.org/pnerg/consul-recipes.svg?branch=master)](https://travis-ci.org/pnerg/consul-recipes)[![codecov](https://codecov.io/gh/pnerg/consul-recipes/branch/master/graph/badge.svg)](https://codecov.io/gh/pnerg/consul-recipes)

# Consul Recipes
Implements the most common "recipes" on [Consul](https://www.consul.io) such as leader election and semaphore.

# Consul
The class `Consul` encapsulates some of the API's towards Consul.  
Instantiated with host information where to access Consul.   


# Leader Election
Leader election also commonly called mutex is the process to elect a single leader.  
Most commonly used in a distributed system where there is a need to coordinate access to something
E.g.
- Create schemas in a database
- Perform periodic pruning of something


Let's break down an example:

```scala
  val observer = new ElectionObserver {
    override def elected(): Unit = println("Elected")
    override def unElected(): Unit = println("UnElected")
  }
  
  val candidate = LeaderElection.joinLeaderElection(ConsulHost("localhost"), "example-group", None, Option(observer)) get
```

The function `LeaderElection.joinLeaderElection` creates a new candidate to join in the election for a named group.  
The new candidate will immediately try to cease leadership byt trying to write to a key named by the provided group name.   

The _observer_ (optional) will be notified of election changes.

Should the current leader yield (`.leave`) or if the leader app crashes then the election group key in Consul is freed and all remaining candidates try to take the election lock.  
This way some other candidate becomes the leader.


# Testing

## Local Consul
To play around with the library one needs an instance of Consul.   
The easiest way is to run Consul locally in a Docker container.  
This command starts a local Consul that can be accessed on [localhost:8500](http://localhost:8500)
```bash
docker run --rm -p 8500:8500 consul:1.3.0 agent -server -bootstrap -ui -client=0.0.0.0
``` 

## Leader Election
Clone this Git repository and run the [ManualLeaderElection](src/test/scala/org/dmonix/consul/ManualLeaderElection.scala) class in your favorite IDE.  
It will start a small app that registers a candidate for the leader election.  
Start multiple instances to make more clients to join, also try killing the elected leader to see how someone else picks up leadership.
