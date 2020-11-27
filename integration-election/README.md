# Integration test - Leader election

Simple app to illustrate the usage of the _leader election_.     
Start multiple instances to have competing users to the election.    
Requires Consul to be listening to localhost:8500.

## Start/stop local Consul
```
docker run -d --rm -p 8500:8500 --name=dev-consul -e CONSUL_BIND_INTERFACE=eth0 consul
```

```
docker rm dev-consul
```
https://hub.docker.com/_/consul

## Run app
```
sbt integrationElection/run
```
Run the above command in multiple shells to connect several clients. 