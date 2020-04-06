# Consul Unit Test Simulator

A very simple simulator that can be used in unit testing.   
The simulator supports a limited set of the API's, currently it is
* session management (v1/session/create|destroy|renew)
* key/value management (v1/kv)

The key/value simulator supports features such as _cas_, _wait_ and _recurse_

Example of usage:  

```
class ConsulWithSimSpec extends Specification with BeforeAfterAll {
  private val consulSim = ConsulSim()

  override def beforeAll = consulSim.start() //starts the simulator on a random port
  override def afterAll = consulSim.shutdown()

  private def consulHost:ConsulHost = consulSim.consulHost.get
  
  your-test-code
  
}  
```

For a full example refer to the test class [ConsulWithSimSpec](../consul-recipes/src/test/scala/org/dmonix/consul/ConsulWithSimSpec.scala)

# Download
The library is cross-compiled for Scala 2.11 and 2.12.  
Latest version is [![consul-sim](https://maven-badges.herokuapp.com/maven-central/org.dmonix/consul-sim.12/badge.svg?style=plastic)](https://search.maven.org/search?q=consul-sim)  
Simply add the following dependency:

```
"org.dmonix" %% "consul-sim" % [version] % "test"
```
