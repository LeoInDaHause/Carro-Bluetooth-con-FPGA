Proyecto Carro a Bluetooth
Para Digital I - Universidad Nacional de Colombia

El proyecto se armó para la materia de Digital I. La idea era aplicar todo lo que vimos en clase usando una FPGA para hacer un carro que se pudiera controlar con el celular.

La meta principal es que este proyecto sirva para que otras personas puedan entender y acceder a los conceptos de la materia.
¿De qué va el proyecto?

Básicamente, construimos un carro a control remoto.

Usamos la Black Ice como la tarjeta principal, es la FPGA que usamos para controlar todo.

El proyecto se centra en:

    El sensor HC-06: Este es el módulo Bluetooth que se encarga de recibir los comandos de la aplicación del celular.

    Dos motores: La FPGA los controla. Dependiendo de lo que llegue por el pin RX de la FPGA desde el Bluetooth, ella sabe cómo moverlos.

La App para el Control

Además de todos los archivos de la lógica del carro, en el repositorio van a encontrar el APK de la aplicación.

Esta aplicación es la que permite controlar:

    Las direcciones del carro (adelante, atrás, etc.).

    La velocidad a la que deben girar los motores.
