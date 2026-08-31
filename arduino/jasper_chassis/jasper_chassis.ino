#include <SoftwareSerial.h>

// HC-06: RX=A0, TX=A1. Телефон шлёт %A# … %S#, Arduino отвечает D:<см>
SoftwareSerial bluetooth(A0, A1);

const int TRIG = A3;
const int ECHO = A2;
const int PWM2A = 11;  // M1
const int PWM2B = 3;   // M2
const int PWM0A = 6;   // M3
const int PWM0B = 5;   // M4
const int DIR_CLK = 4;
const int DIR_EN = 7;
const int DATA = 8;
const int DIR_LATCH = 12;

const int Move_Forward = 39;
const int Move_Backward = 216;
const int Left_Move = 116;
const int Right_Move = 139;
const int Right_Rotate = 149;
const int Left_Rotate = 106;
const int Stop = 0;
const int Upper_Left_Move = 36;
const int Upper_Right_Move = 3;
const int Lower_Left_Move = 80;
const int Lower_Right_Move = 136;

// Скорость, на которой пишете датасет. Менять и перепрошивать до записи.
const int SPEED = 200;

const unsigned long SONAR_PERIOD_MS = 120;

String incoming;
unsigned long lastSonarMs = 0;

void setMotors(int dir, int s1, int s2, int s3, int s4) {
  analogWrite(PWM2A, s1);
  analogWrite(PWM2B, s2);
  analogWrite(PWM0A, s3);
  analogWrite(PWM0B, s4);
  digitalWrite(DIR_LATCH, LOW);
  shiftOut(DATA, DIR_CLK, MSBFIRST, dir);
  digitalWrite(DIR_LATCH, HIGH);
}

int pingCm() {
  digitalWrite(TRIG, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG, LOW);
  unsigned long us = pulseIn(ECHO, HIGH, 30000UL);
  if (us == 0) return 0;
  int cm = (int)(us / 58UL);
  if (cm < 0) return 0;
  if (cm > 400) return 400;
  return cm;
}

void sendSonar() {
  int cm = pingCm();
  bluetooth.print("D:");
  bluetooth.println(cm);
}

void setup() {
  bluetooth.begin(9600);
  Serial.begin(9600);
  pinMode(DIR_CLK, OUTPUT);
  pinMode(DATA, OUTPUT);
  pinMode(DIR_EN, OUTPUT);
  pinMode(DIR_LATCH, OUTPUT);
  pinMode(PWM0B, OUTPUT);
  pinMode(PWM0A, OUTPUT);
  pinMode(PWM2A, OUTPUT);
  pinMode(PWM2B, OUTPUT);
  pinMode(TRIG, OUTPUT);
  pinMode(ECHO, INPUT);
  digitalWrite(DIR_EN, LOW);
  setMotors(Stop, 0, 0, 0, 0);
}

void loop() {
  while (bluetooth.available() > 0) {
    incoming += (char)bluetooth.read();
    delay(2);
  }

  if (incoming.length() >= 3 &&
      incoming.charAt(0) == '%' &&
      incoming.charAt(incoming.length() - 1) == '#') {
    char cmd = incoming.charAt(1);
    incoming = "";
    switch (cmd) {
      case 'A':
        setMotors(Move_Forward, SPEED, SPEED, SPEED, SPEED);
        break;
      case 'B':
        setMotors(Move_Backward, SPEED, SPEED, SPEED, SPEED);
        break;
      case 'C':
        setMotors(Left_Move, SPEED, SPEED, SPEED, SPEED);
        break;
      case 'D':
        setMotors(Right_Move, SPEED, SPEED, SPEED, SPEED);
        break;
      case 'E':
        setMotors(Left_Rotate, SPEED, SPEED, SPEED, SPEED);
        break;
      case 'F':
        setMotors(Right_Rotate, SPEED, SPEED, SPEED, SPEED);
        break;
      case 'G':
        setMotors(Upper_Left_Move, SPEED, SPEED, SPEED, SPEED);
        break;
      case 'H':
        setMotors(Upper_Right_Move, SPEED, SPEED, SPEED, SPEED);
        break;
      case 'I':
        setMotors(Lower_Left_Move, SPEED, SPEED, SPEED, SPEED);
        break;
      case 'J':
        setMotors(Lower_Right_Move, SPEED, SPEED, SPEED, SPEED);
        break;
      case 'S':
        setMotors(Stop, 0, 0, 0, 0);
        break;
      default:
        break;
    }
  } else if (incoming.length() > 8) {
    incoming = "";
  }

  unsigned long now = millis();
  if (now - lastSonarMs >= SONAR_PERIOD_MS) {
    lastSonarMs = now;
    sendSonar();
  }
}
