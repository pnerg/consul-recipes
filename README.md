# Consul Recipes & Consul Unit Test Simulator

This projects hosts two libraries
* [consul-recipes](consul-recipes/README.md) Implements the most common "recipes" on [Consul](https://www.consul.io) such as leader election and semaphore.
* [consul-sim](consul-sim/README.md) A simple simulator (not full implementation) of some of the HTTP API's to Consul. Very useful for e.g. unit testing

As well as two test sub-projects for playing with the functionality locally.
* [integration-election](integration-election/README.md) Simple app to illustrate the usage of the _leader election_. 
* [integration-semaphore](integration-semaphore/README.md) Simple app to illustrate the usage of the _semaphore_. 

# Download
Both libraries are cross-compiled for Scala 2.11, 2.12 and 2.13.  
Latest version is [![consul-recipes](https://maven-badges.herokuapp.com/maven-central/org.dmonix/consul-recipes_2.12/badge.svg?style=plastic)](https://search.maven.org/search?q=consul-recipes)  
Simply add the following dependency:
```
"org.dmonix" %% "consul-recipes" % [version]
```

```
"org.dmonix" %% "consul-sim" % [version] % "test"
```
