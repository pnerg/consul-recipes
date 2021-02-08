# Consul Recipes & Consul Unit Test Simulator
![test](https://github.com/pnerg/consul-recipes/workflows/Build%20&%20Test/badge.svg) 
[![codecov](https://codecov.io/gh/pnerg/consul-recipes/branch/master/graph/badge.svg?token=oIoFlTyu5A)](https://codecov.io/gh/pnerg/consul-recipes)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.dmonix/consul-recipes_2.13/badge.svg?style=plastic)](https://maven-badges.herokuapp.com/maven-central/org.dmonix/consul-recipes_2.13) 
[![Scaladoc](http://javadoc-badge.appspot.com/org.dmonix/consul-recipes_2.13.svg?label=scaladoc)](http://javadoc-badge.appspot.com/org.dmonix/consul-recipes_2.13)
 
This projects hosts two libraries
* [consul-recipes](consul-recipes/README.md) Implements the most common "recipes" on [Consul](https://www.consul.io) such as leader election and semaphore.
* [consul-sim](consul-sim/README.md) A simple simulator (not full implementation) of some of the HTTP API's to Consul. Very useful for e.g. unit testing

As well as two test sub-projects for playing with the functionality locally.
* [integration-election](integration-election/README.md) Simple app to illustrate the usage of the _leader election_. 
* [integration-semaphore](integration-semaphore/README.md) Simple app to illustrate the usage of the _semaphore_. 

# Download
Both libraries are cross-compiled for Scala 2.11, 2.12 and 2.13.  
Simply add the following dependency:
```
"org.dmonix" %% "consul-recipes" % [version]
```

```
"org.dmonix" %% "consul-sim" % [version] % "test"
```
