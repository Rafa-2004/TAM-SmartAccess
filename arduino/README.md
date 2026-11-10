# arduino

Firmware do Arduino UNO WiFi Rev2 (RC522, 2x HC-SR04, servo, LCD I2C, buzzer, WiFi).

- Abrir `arduino/arduino.ino` no Arduino IDE (a pasta e o ficheiro tem de ter o mesmo nome).
- Copiar `arduino_secrets.h.example` para `arduino_secrets.h` e preencher a rede WiFi (2.4 GHz). O `arduino_secrets.h` esta no `.gitignore`.
- Placa: Arduino UNO WiFi Rev2 (megaAVR). Bibliotecas: MFRC522, LiquidCrystal_I2C, WiFiNINA, ArduinoHttpClient, Servo.
- No 1.o arranque o Arduino regista-se na API pelo MAC e fica "pendente" ate um administrador o aprovar.
- Comandos no Monitor Serie: ver `?` (simular cartao, entrada/saida, distancias, estado, apagar registo).
