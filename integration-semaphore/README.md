# Integration test - Semaphore

Simple app to illustrate the usage of a _Semaphore_.     
Start multiple instances to have competing users to the Semaphore.    
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
sbt integrationSemaphore/run
```
Run the above command in multiple shells to connect several clients. 