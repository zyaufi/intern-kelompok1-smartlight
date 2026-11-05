import serial
import zmq
import time
import threading
import sys

PORT = 'COM6'
BAUD_RATE = 115200
ZMQ_PORT = 5542

try:
    ser = serial.Serial(PORT, BAUD_RATE, timeout=1)
    time.sleep(1) 
    print(f" Terhubung ke STM32 di port {PORT}")
except serial.SerialException as e:
    print(f" Gagal membuka port serial: {e}")
    sys.exit()

context = zmq.Context()
socket = context.socket(zmq.REP)
socket.bind(f"tcp://*:{ZMQ_PORT}")
print(f" Server ZeroMQ AKTIF di port {ZMQ_PORT}")
print(" Menunggu perintah dari Android...")

current_voltage = "0.00"

def read_serial():
    global current_voltage
    while True:
        try:
            if ser.in_waiting > 0:
                line = ser.readline().decode('utf-8', errors='ignore').strip()
                
                if line:
                  
                    if line.startswith("V:"):
                        current_voltage = line[2:]
                        print(f" Tegangan STM: {current_voltage} V")
                    elif any(char.isdigit() for char in line):
                        current_voltage = line
                        print(f" Tegangan STM: {current_voltage} V")
                        
        except Exception as e:
            
            print(f" Error baca serial: {e}")
            
        time.sleep(0.05)

threading.Thread(target=read_serial, daemon=True).start()

try:
    while True:
        message = socket.recv_string()
        print(f" ANDROID Masuk: {message}")
        
        try:
            if message == "TEGANGAN":
                socket.send_string(current_voltage)
                
            elif message == "1":
                ser.write(b"1\n")
                socket.send_string("OK")
                print(" STM32 Kirim: ON")
                
            elif message == "0":
                ser.write(b"0\n")
                socket.send_string("OK")
                print(" STM32 Kirim: OFF")
                
            # elif message.startswith("MODE:") or message.startswith("BRIGHT:"):
            #     ser.write(f"{message}\n".encode())
            #     socket.send_string("OK")
            #     print(f" STM32 Kirim: {message}")
                
            else:
                socket.send_string("UNKNOWN")
                
        except Exception as e:
            print(f" Error proses: {e}")
            socket.send_string("ERROR")

except KeyboardInterrupt:
    print("\n Server dimatikan.")
finally:
    ser.close()
    socket.close()
    context.term()