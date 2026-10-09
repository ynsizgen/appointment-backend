# Postman kullanımı

Import edilecek dosya: `Appointment Platform.postman_collection.json`.
Bu README yalnızca kullanım rehberidir; Postman’a import edilmez.

## Klasörler

| Klasör | İçerik |
| --- | --- |
| Authentication / Session | CSRF alma, yenileme, mevcut hesap, logout |
| Authentication / Registration | Brand kaydı ve e-posta doğrulama |
| Authentication / Login | Brand veya admin girişi |
| Brand | Giriş yapan brand bilgisi |
| Branches | Şubeler ve randevu kabul ayarı |
| Services | Hizmet oluşturma ve listeleme |
| Employees | Çalışan oluşturma ve listeleme |
| Employees / Service Assignments | Assign, atanmış hizmetleri listeleme, unassign |
| Admin | Hesapları listeleme ve durum değiştirme |
| System | Health ve doğrulama sayfasının HTML/JS kontrolleri |

`Validation Checks` klasörleri bilerek hatalı istekler içerir; beklenen 400/409 cevapları başarılı test sayılır.

## Başlangıç

1. Collection Variables içinde `baseUrl` ve kullanacağın hesabın bilgilerini ayarla.
2. Authentication → Session → Get CSRF çalıştır.
3. Authentication → Login içinden **Brand Login veya Admin Login** seç. İkisini arka arkaya çalıştırma.
4. Authentication → Session → Refresh CSRF After Login çalıştır.
5. İlgili işlem klasörüne geç. Postman session cookie’sini otomatik taşır.

## Employee–service işlemleri

Brand olarak giriş yaptıktan sonra:

1. Branches → Get Own Branches: ilk şubenin ID’sini `branchId` olarak kaydeder.
2. Services → Get Own Services: ilk hizmetin ID’sini `serviceId` olarak kaydeder. Liste boşsa önce Add Service kullan.
3. Employees → Add Employee: oluşturulan ID’yi `employeeId` olarak kaydeder. Mevcut çalışanı kullanmak için değişkeni elle ayarla.
4. Employees → Service Assignments → Assign Service.
5. Get Assigned Services ile sonucu gör.
6. İstersen Validation Checks → Reject Duplicate Assignment çalıştır; 409 beklenir.
7. Unassign Service çalıştır, sonra Get Assigned Services ile eşleştirmenin kaldırıldığını kontrol et.

Başka kayıtlarla çalışmak için collection variables içindeki ID’leri değiştir.
Bütün collection tek bir senaryo olarak çalıştırılmamalı: girişler rol değiştirir, kayıt işlemi e-posta token’ı bekler ve bazı istekler mevcut veriyi değiştirir.

Dosya güncellendiğinde Postman’a yeniden import et. Aynı isimli eski collection ile karıştırma; mevcut özel değişken değerlerini önce sakla.
