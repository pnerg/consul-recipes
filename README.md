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


# Semaphore
A semaphore is a construction to control access to a common resource in a concurrent system.  
The semaphore in this library allows for creation of a distributed lock. 
In principle a semaphore is initiated with _1..n_ permits, instances of a semaphore then try to acquire one permit either succeeding or failing in case there are no more permits left to take.  
The simplest form is a binary semaphore which has only one permit thus only allowing a single instance to acquire a permit.  
Semaphores are traditionally used to control access to a protected source like a database or some task that only should be executed by a single process.     

Each instance of the _Semaphore_ class can hold exactly 0 or 1 permit.  

It all starts by creating a Semaphore instance.  
Note the _.get_ in the of this example end as the result is a `Try[Semaphore]`.
```scala
val semaphore = Semaphore(ConsulHost("localhost"), "example-semaphore", 1).get
````
Simply trying to take a permit yields a `Try[Boolean]`.  
The function attempts to take a permit and returns immediately with the result.  
Invoking _tryAcquire_ on a semaphore instance that already holds a permit will return _true_ but it will not take another permit as each instance can only hold one permit.
```scala
 semaphore.tryAcquire() match {
    case Success(true) => println("Yay, got a permit!")
    case Success(false) => println("Didn't get a permit...:(")
    case Failure(SemaphoreDestroyed(name)) => println(s"Semaphore [$name] was destroyed, just keep doing something else")
    case Failure(ex) => ex.printStackTrace()
  }
```
A variant is to perform a blocking operation trying to take a permit.   
The function will return _false_ in case it could not acquire a permit within the required deadline.
```scala
 semaphore.tryAcquire(5.minutes) match {
    case Success(true) => println("Yay, got a permit!")
    case Success(false) => println("Didn't get a permit...:(")
    case Failure(SemaphoreDestroyed(name)) => println(s"Semaphore [$name] was destroyed, just keep doing something else")
    case Failure(ex) => ex.printStackTrace()
  }
```

Releasing a permit will first destroy the session held by the semaphore instance and then only actually release a permit if the semaphore instance held one.   
The result is a `Try[Boolean]` where the boolean marks if a permit was released or not.   

```scala
semaphore.release()
```

One can also permantently destroy a semaphore and in the process also release all instances blocking for a permit.  
This will delete all files in Consul related to the semaphore and in the process all blocking instances are released with a `Failure(SemaphoreDestroyed)`
```scala
semaphore.destroy()
```

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

## Semaphore
Clone this Git repository and run the [ManualSemaphore](src/test/scala/org/dmonix/consul/ManualSemaphore.scala) class in your favorite IDE.  
It will start a small app that tries to register a semaphore and try to take a permit.  
Start multiple instances to make more instances to join, also try killing the instance that has a permit to see how someone else is released.

# Download
The project is cross-compiled for Scala 2.11 and 2.12.  
Latest version is [![consul-recipes](https://maven-badges.herokuapp.com/maven-central/org.dmonix/consul-recipes_2.12/badge.svg?style=plastic)](https://search.maven.org/search?q=consul-recipes)  
Simply add the following dependency:
```
"org.dmonix" %% "consul-recipes" % [version]
```
All available versions are found on [Maven Central](https://search.maven.org/search?q=consul-recipes)