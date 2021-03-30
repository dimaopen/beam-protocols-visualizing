BEAM Protocol Visualizer
===============

Converts [beam](https://github.com/LBNL-UCB-STI/beam) actor messages to a sequence diagram. These messages are written when the beam config file contains
akka.actor.debug.receive=true
In this case beam produces files like 0.actor_messages_0.csv.gz in each iteration folder.

Parameters
----------
```
--input output/sf-light/run_name/ITERS/it.0
--output docs/uml/choose_mode.puml
--force
--person-id 010900-2012001379980-0-560057
```

- **input** directory where to read message files,
- **output** the output file,
- **force** allows to overwrite the output without prompt
- **person-id** the person id which the message sequence should be generated for. If not set then all the messages are processed.


Used libraries
--------------
This app is build upon [FS2](https://fs2.io). CSV processing is done with [fs2-data](https://fs2-data.gnieh.org/). For capturing and controlling actions (file system operations, user interactions) [Cats Effect](https://typelevel.org/cats-effect/) is used.