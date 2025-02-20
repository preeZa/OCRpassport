## **OCR Passport & NFC Reader**

📌 **คำอธิบาย**

โปรเจกต์นี้เป็นระบบที่สามารถอ่านข้อมูลจาก เอกสารเดินทาง (Travel Document) และ พาสปอร์ต (Passport) ด้วยเทคโนโลยี OCR และ NFC โดยมีเป้าหมายเพื่อ:

- **OCR**: ดึงข้อมูลจาก MRZ (Machine Readable Zone) ในพาสปอร์ตและเอกสารเดินทาง
- **Text Processing**: แปลงข้อมูล OCR เป็นข้อความที่นำไปใช้ได้
- **NFC**: ใช้ข้อมูลจาก OCR เพื่อดึงข้อมูลเพิ่มเติมที่แม่นยำจากชิป NFC ในพาสปอร์ต เช่น รูปภาพและข้อมูลส่วนบุคคล

🚀 **Features**

- ✅ อ่านข้อมูลจาก MRZ ในพาสปอร์ตและ Travel Document ด้วย OCR
- ✅ แปลงข้อมูล MRZ เป็นข้อความ
- ✅ อ่านข้อมูลจากชิป NFC ในพาสปอร์ต
- ✅ ดึงข้อมูลส่วนบุคคลและรูปภาพจาก NFC 

**อธิบายการทำงาน**
- MainOcrPassportActivity ใน activity นี้คือ
  - การทำงานขอ permission กล้อง
  - เปิด CameraPreviewActivity และรอรับ result
  - เปิด NfcReadingActivity และรอรับ result
  - show dialog result ที่ได้
- CameraPreviewActivity ทำหน้าที่
  - Scan traval doc แล้ว return กลับเป็นข้อมูลที่อยู่ใน mrz
- NfcReadingActivity ทำหน้าที่
  - ส่งข้อมูลไปยัง traval doc เพื่อดึงข้อมูลเพิ่มเติมที่แม่นยำจากชิป NFC ในพาสปอร์ แล้ว return กลับเป็นข้อมูลส่วนตัวและรูปภาพ

