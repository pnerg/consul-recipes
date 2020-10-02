# Changelog

All notable changes to this project will be documented in this file.

## [0.5.0]

### Added

- Suppport for the 'Flags' attribute when fetching key data from Consul
    - The 'Flags' attribute was also added to the [Consul Simulator](consul-sim/README.md) 

### Changed

## [0.4.0]

### Added

- Refactored out the [Consul Simulator](consul-sim/README.md) written for testing to its own delivareble binary. 
This way it can be used for other projects needing to perform unit testing towards Consul
   

### Changed

## [0.3.0]

### Added


### Changed

- Bug fix: Trimming responses from Consul before parsing them as booleans, sometimes there's a 'CR' char