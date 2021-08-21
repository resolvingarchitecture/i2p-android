# I2P Android Client Service
Invisible Internet Project (I2P) Client Service for interacting with local Android I2P router. 
Connects to the local I2P Router as a service so that it can be easily managed
and used by 3rd party decentralized applications.

## Build Notes

## Roadmap


## Installation
Router with original UI:

https://geti2p.net/en/download

I2P Zero - zero dependency build of I2P router with minimal gui

https://github.com/i2p-zero/i2p-zero

* Ensure your firewall is opened to the random port assigned on startup for both tcp and udp.
* Open port 123 on udp for time sync if on (default)

## Removal

### Raspian

#### I2P External Router
Note: Not yet supported
```
sudo apt remove i2p
sudo apt remove i2prouter
sudo apt autoremove
sudo apt autoclean
```

## Attack Mitigation

- https://www.irongeek.com/i.php?page=security/i2p-identify-service-hosts-eepsites

## Version Notes
