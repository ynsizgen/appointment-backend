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
| Branches | Branch oluşturma, haftalık çalışma saatleri ve randevu kabul ayarı |
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

1. İlk girişte Branches → Create Branch ile adres, açık günler ve saatleri gir. Örnekte pazartesi–cumartesi 08.00–20.00 vardır; günleri ve saatleri değiştirebilirsin. Registration artık branch oluşturmaz. Mevcut branch için Get Own Branches ilk şubenin ID’sini `branchId` olarak kaydeder.
2. Services → Get Own Services: ilk hizmetin ID’sini `serviceId` olarak kaydeder. Liste boşsa önce Add Service kullan.
3. Employees → Add Employee: oluşturulan ID’yi `employeeId` olarak kaydeder. Mevcut çalışanı kullanmak için değişkeni elle ayarla.
4. Employees → Service Assignments → Assign Service.
5. Get Assigned Services ile sonucu gör.
6. İstersen Validation Checks → Reject Duplicate Assignment çalıştır; 409 beklenir.
7. Unassign Service çalıştır, sonra Get Assigned Services ile eşleştirmenin kaldırıldığını kontrol et.

Başka kayıtlarla çalışmak için collection variables içindeki ID’leri değiştir.
Bütün collection tek bir senaryo olarak çalıştırılmamalı: girişler rol değiştirir, kayıt işlemi e-posta token’ı bekler ve bazı istekler mevcut veriyi değiştirir.

Dosya güncellendiğinde Postman’a yeniden import et. Aynı isimli eski collection ile karıştırma; mevcut özel değişken değerlerini önce sakla.

Branch çalışma saatlerini Get Working Hours ile okuyabilir, Replace Working Hours ile haftanın tamamı için değiştirebilirsin. Listede olmayan günler kapalıdır; boş liste bütün günleri kapatır.

## Employee çalışma saatleri

`Employees → Working Hours` altında haftalık ve tarihe özel işlemler bulunur.

- İlk durumda employee branch saatlerini kullanır.
- Set Weekly Hours: tüm haftayı değiştirir; olmayan günler kapalıdır. Boş liste employee'yi bütün hafta kapatır.
- Reset to Branch Hours: özel haftalık saatleri kaldırır. Tarih bazlı kayıtlar korunur.
- Tarih istekleri için `scheduleDate` değişkenine `YYYY-MM-DD` biçiminde bugün veya gelecek bir tarih yaz.
- Close Employee for Date: yalnız o tarihi kapatır. Reset Date to Weekly Hours: o gün için haftalık saatlere geri döner.
- Get Effective Hours for Date: `configuredHours` seçilen saatler, `workingHours` branch sınırları uygulanmış saatlerdir. Employee pasifse `workingHours` boştur.
- Bu sonuçlar randevu slotları değildir; mevcut randevuları çıkarma ve çakışma kontrolü appointment modülünde eklenecek.

### Working Hours klasör düzeni ve 404 kontrolü

- **Weekly Schedule:** haftalık saatleri gör, değiştir veya branch saatlerine geri dön.
- **Specific Date:** seçilen tarihin saatlerini gör, değiştir, günü kapat veya haftalık ayara geri dön.
- DELETE istekleri employee kaydını silmez; yalnız ilgili özel saat ayarını kaldırır.

Get Weekly Hours URL: `{{baseUrl}}/api/panel/employee/{{employeeId}}/working-hours`.
Önce aynı `employeeId` ile Get Employee çalıştır. Employee bulunamıyorsa seçilen ID'yi kontrol et. Şube bulunamıyorsa giriş yapılan brand ile employee'nin branch'i eşleşmiyor olabilir.
Get Employee 200, Get Weekly Hours 404 ise çalışan backend'in yeni controller'ı içerdiğini kontrol et; backend'i yeniden başlat ve Postman URL'sini yukarıdaki adresle karşılaştır.
Postman environment değişkenleri collection değişkenlerini geçersiz kılabilir; `employeeId` için kullanılan gerçek değeri kontrol et.
Saat kaydı olmaması 404 üretmez: branch saatleri kullanılır; branch'te de saat yoksa boş liste döner.

## Branch için belirli bir tarih

`Branches → Specific Date` altında Get / Set / Close / Reset istekleri bulunur.
`branchId` ve `scheduleDate` değişkenlerini seç. Tarih URL'dedir; body yalnız `workingHours` içerir.
Boş liste o gün branch'i kapatır; employee özel saatleri bunu aşamaz. Reset yalnız tarih kaydını kaldırır, haftalık saatleri geri getirir.
Set ile normalde kapalı bir gün açılabilir. Branch saatlerini kullanan employee'ler bu yeni saatleri kullanır; kendi haftalık ayarı olan employee'ler o ayarla sınırlı kalır.
