# Worker-java
Worker component refactor project

## About Env

CONFIG_FILE_PATH : the config path

## About properties

properties file is out of the jar, which discovery depends on `CONFIG_FILE_PATH`

Worker will read default from config.properties, which is the same directory as the production jar.


## About Development

If you use macOS, please read spotify docker readme.
If you use windows with docker desktop, please set the router table entry for the test to connection with runtime containers.
default the command is:
`route -p add 172.17.0.0 MASK 255.255.255.0 10.0.75.2`


## About Test

If you use IDEA, please create task use the `$FileDir$` as Working Directory because of read config_test.properties

## About Deployment

now Work-Java must be deployed in the host network namespace, which means if you use docker to run Worker-java, you should add
`--network host`, or you can just run the jar in the host.

**Please use IPADS internal proto instead. and you maybe should change proto import path.**

If you have any problem about proto, you can connect to [LightDrizzle](https://github.com/tx19980520)
