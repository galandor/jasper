#include <SoftwareSerial.h>

// Два режима:
//   LEGACY — старая прошивка машинки: импульсы A–L, ультразвук сам рулит на T/W,
//            нет команды → стоп. Джойстик приложения / «погуляй» как раньше.
//   PHONE  — телефон забрал шасси. Моторы держат последнюю команду, ультразвук
//            не вмешивается в A–L. Вход: %P# (Jasper шлёт при коннекте).
//            Выход: %O#, или тишина ~15 с (телефон отвалился).

SoftwareSerial mySerial(A0, A1); // RX, TX
String BT_value;
String BT_value_temp;
String phoneRx;
volatile int Front_Distance;
volatile boolean Flag = true;

const int Trig = A3;
const int Echo = A2;
const int PWM2A = 11;      // M1 motor
const int PWM2B = 3;       // M2 motor
const int PWM0A = 6;       // M3 motor
const int PWM0B = 5;       // M4 motor
const int DIR_CLK = 4;     // Data input clock line
const int DIR_EN = 7;      // Equip the L293D enabling pins
const int DATA = 8;        // USB cable
const int DIR_LATCH = 12;  // Output memory latch clock

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
const int Drift_Left = 20;
const int Drift_Right = 10;

int Speed1 = 150;
int Speed2 = 150;
int Speed3 = 150;
int Speed4 = 150;

enum Mode {
  MODE_LEGACY = 0,
  MODE_PHONE = 1
};

Mode mode = MODE_LEGACY;
char phoneLatched = 0;
unsigned long lastRxMs = 0;
unsigned long lastDriveMs = 0;

const unsigned long DRIVE_WATCHDOG_MS = 800;
const unsigned long PHONE_REVERT_MS = 15000;

void Motor(int Dir, int Speed1, int Speed2, int Speed3, int Speed4) {
  analogWrite(PWM2A, Speed1);
  analogWrite(PWM2B, Speed2);
  analogWrite(PWM0A, Speed3);
  analogWrite(PWM0B, Speed4);

  digitalWrite(DIR_LATCH, LOW);
  shiftOut(DATA, DIR_CLK, MSBFIRST, Dir);
  digitalWrite(DIR_LATCH, HIGH);
}

void motorsStop() {
  Motor(Stop, 0, 0, 0, 0);
}

float checkdistance() {
  digitalWrite(Trig, LOW);
  delayMicroseconds(2);
  digitalWrite(Trig, HIGH);
  delayMicroseconds(10);
  digitalWrite(Trig, LOW);
  float distance = pulseIn(Echo, HIGH) / 58.00;
  delay(10);
  return distance;
}

void Ultrasonic_Avoidance() {
  int Front_Distance = 0;

  Front_Distance = checkdistance();
  if (0 < Front_Distance && Front_Distance <= 45) {
    if (Front_Distance <= 20) {
      Flag = !Flag;
      Motor(Stop, 0, 0, 0, 0);
      delay(250);
      Motor(Move_Backward, Speed1, Speed2, Speed3, Speed4);
      delay(200);
      Motor(Stop, 0, 0, 0, 0);
      delay(250);
      if (Flag) {
        Motor(Left_Rotate, Speed1, Speed2, Speed3, Speed4);
      } else {
        Motor(Right_Rotate, Speed1, Speed2, Speed3, Speed4);
      }
      delay(100);
      Motor(Stop, 0, 0, 0, 0);
      delay(250);
    } else {
      Motor(Stop, 0, 0, 0, 0);
      delay(250);
      if (Flag) {
        Motor(Left_Rotate, Speed1, Speed2, Speed3, Speed4);
      } else {
        Motor(Right_Rotate, Speed1, Speed2, Speed3, Speed4);
      }
      delay(100);
      Motor(Stop, 0, 0, 0, 0);
      delay(250);
    }
  } else {
    Motor(Move_Forward, 100, 100, 100, 100);
  }
}

void Ultrasonic_Follow() {
  Front_Distance = checkdistance();
  if ((Front_Distance >= 0) && (Front_Distance <= 10)) {
    Motor(Move_Backward, Speed1, Speed2, Speed3, Speed4);
    delay(20);
  } else if ((Front_Distance > 10) && (Front_Distance <= 15)) {
    Motor(Stop, 0, 0, 0, 0);
    delay(20);
  } else {
    Motor(Move_Forward, 170, 170, 170, 170);
    delay(20);
  }
}

bool applyDriveLetter(char cmd) {
  switch (cmd) {
    case 'A':
      Motor(Move_Forward, Speed1, Speed2, Speed3, Speed4);
      return true;
    case 'B':
      Motor(Move_Backward, Speed1, Speed2, Speed3, Speed4);
      return true;
    case 'C':
      Motor(Left_Move, Speed1, Speed2, Speed3, Speed4);
      return true;
    case 'D':
      Motor(Right_Move, Speed1, Speed2, Speed3, Speed4);
      return true;
    case 'E':
      Motor(Left_Rotate, Speed1, Speed2, Speed3, Speed4);
      return true;
    case 'F':
      Motor(Right_Rotate, Speed1, Speed2, Speed3, Speed4);
      return true;
    case 'G':
      Motor(Upper_Left_Move, Speed1, Speed2, Speed3, Speed4);
      return true;
    case 'H':
      Motor(Upper_Right_Move, Speed1, Speed2, Speed3, Speed4);
      return true;
    case 'I':
      Motor(Lower_Left_Move, Speed1, Speed2, Speed3, Speed4);
      return true;
    case 'J':
      Motor(Lower_Right_Move, Speed1, Speed2, Speed3, Speed4);
      return true;
    case 'K':
      Motor(Drift_Left, Speed1, Speed2, Speed3, Speed4);
      return true;
    case 'L':
      Motor(Drift_Right, Speed1, Speed2, Speed3, Speed4);
      return true;
    default:
      return false;
  }
}

void enterPhone() {
  mode = MODE_PHONE;
  phoneLatched = 0;
  phoneRx = "";
  BT_value = "";
  BT_value_temp = "";
  lastRxMs = millis();
  motorsStop();
  Serial.println(F("mode=PHONE"));
}

void enterLegacy() {
  mode = MODE_LEGACY;
  phoneLatched = 0;
  phoneRx = "";
  BT_value = "";
  BT_value_temp = "";
  motorsStop();
  Serial.println(F("mode=LEGACY"));
}

void handlePhoneCmd(char cmd) {
  lastRxMs = millis();
  switch (cmd) {
    case 'P':
      break;
    case 'O':
      enterLegacy();
      break;
    case 'S':
      phoneLatched = 0;
      motorsStop();
      break;
    case 'T':
      phoneLatched = 'T';
      break;
    case 'W':
      phoneLatched = 'W';
      break;
    default:
      if (applyDriveLetter(cmd)) {
        phoneLatched = cmd;
        lastDriveMs = millis();
      }
      break;
  }
}

void drainPhoneCommands() {
  while (mySerial.available() > 0) {
    char c = (char)mySerial.read();
    lastRxMs = millis();
    phoneRx += c;
    if (phoneRx.length() > 24) {
      phoneRx = phoneRx.substring(phoneRx.length() - 12);
    }
  }

  int start = phoneRx.indexOf('%');
  while (start >= 0 && start + 2 < (int)phoneRx.length()) {
    if (phoneRx.charAt(start + 2) == '#') {
      char cmd = phoneRx.charAt(start + 1);
      phoneRx.remove(0, start + 3);
      handlePhoneCmd(cmd);
      if (mode != MODE_PHONE) {
        return;
      }
      start = phoneRx.indexOf('%');
    } else {
      phoneRx.remove(0, start + 1);
      start = phoneRx.indexOf('%');
    }
  }
}

void loopPhone() {
  drainPhoneCommands();
  if (mode != MODE_PHONE) {
    return;
  }

  unsigned long now = millis();
  if (phoneLatched == 'T') {
    Ultrasonic_Avoidance();
  } else if (phoneLatched == 'W') {
    Ultrasonic_Follow();
  } else if (phoneLatched != 0 && (now - lastDriveMs) > DRIVE_WATCHDOG_MS) {
    phoneLatched = 0;
    motorsStop();
  }

  if ((now - lastRxMs) > PHONE_REVERT_MS) {
    enterLegacy();
  }
}

void loopLegacy() {
  while (mySerial.available() > 0) {
    BT_value_temp = BT_value_temp + ((char)(mySerial.read()));
    delay(2);
    if (!mySerial.available() > 0) {
      BT_value = BT_value_temp;
      BT_value_temp = "";
    }
  }
  if (0 < String(BT_value).length()) {
    Serial.println(BT_value);
    if (4 >= String(BT_value).length()) {
      if ('%' == String(BT_value).charAt(0) && '#' == String(BT_value).charAt((String(BT_value).length() - 1))) {
        switch (String(BT_value).charAt(1)) {
          case 'P':
            enterPhone();
            return;
          case 'O':
            BT_value = "";
            motorsStop();
            break;
          case 'A':
            Motor(Move_Forward, Speed1, Speed2, Speed3, Speed4);
            delay(200);
            BT_value = "";
            break;
          case 'B':
            Motor(Move_Backward, Speed1, Speed2, Speed3, Speed4);
            delay(200);
            BT_value = "";
            break;
          case 'C':
            Motor(Left_Move, Speed1, Speed2, Speed3, Speed4);
            delay(200);
            BT_value = "";
            break;
          case 'D':
            Motor(Right_Move, Speed1, Speed2, Speed3, Speed4);
            delay(200);
            BT_value = "";
            break;
          case 'E':
            Motor(Left_Rotate, Speed1, Speed2, Speed3, Speed4);
            delay(100);
            BT_value = "";
            break;
          case 'F':
            Motor(Right_Rotate, Speed1, Speed2, Speed3, Speed4);
            delay(100);
            BT_value = "";
            break;
          case 'G':
            Motor(Upper_Left_Move, Speed1, Speed2, Speed3, Speed4);
            delay(300);
            BT_value = "";
            break;
          case 'H':
            Motor(Upper_Right_Move, Speed1, Speed2, Speed3, Speed4);
            delay(300);
            BT_value = "";
            break;
          case 'I':
            Motor(Lower_Left_Move, Speed1, Speed2, Speed3, Speed4);
            delay(300);
            BT_value = "";
            break;
          case 'J':
            Motor(Lower_Right_Move, Speed1, Speed2, Speed3, Speed4);
            delay(300);
            BT_value = "";
            break;
          case 'K':
            Motor(Drift_Left, Speed1, Speed2, Speed3, Speed4);
            delay(300);
            BT_value = "";
            break;
          case 'L':
            Motor(Drift_Right, Speed1, Speed2, Speed3, Speed4);
            delay(300);
            BT_value = "";
            break;
          case 'T':
            Ultrasonic_Avoidance();
            break;
          case 'W':
            Ultrasonic_Follow();
            break;
          case 'S':
            BT_value = "";
            Motor(Stop, 0, 0, 0, 0);
            break;
        }
      }
    } else {
      BT_value = "";
      Motor(Stop, 0, 0, 0, 0);
    }
  } else {
    Motor(Stop, 0, 0, 0, 0);
  }
}

void setup() {
  BT_value = "";
  BT_value_temp = "";
  phoneRx = "";
  Front_Distance = 0;

  mySerial.begin(9600);
  Serial.begin(9600);
  pinMode(DIR_CLK, OUTPUT);
  pinMode(DATA, OUTPUT);
  pinMode(DIR_EN, OUTPUT);
  pinMode(DIR_LATCH, OUTPUT);
  pinMode(PWM0B, OUTPUT);
  pinMode(PWM0A, OUTPUT);
  pinMode(PWM2A, OUTPUT);
  pinMode(PWM2B, OUTPUT);
  pinMode(Trig, OUTPUT);
  pinMode(Echo, INPUT);
  motorsStop();
  Serial.println(F("mode=LEGACY"));
}

void loop() {
  if (mode == MODE_PHONE) {
    loopPhone();
  } else {
    loopLegacy();
  }
}
