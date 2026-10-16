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
- Set Weekly Hours: haftalık özel ayarların tamamını değiştirir. `workingHours` içindeki günler özel saatleri kullanır, `closedDays` içindeki günler kapalıdır. İkisinde de olmayan günler branch saatlerini kullanır. İki liste de boşsa bütün günler branch'ten alınır.
- Reset to Branch Hours: özel haftalık saatleri kaldırır. Tarih bazlı kayıtlar korunur.
- Tarih istekleri için `scheduleDate` değişkenine `YYYY-MM-DD` biçiminde bugün veya gelecek bir tarih yaz.
- Close Employee for Date: yalnız o tarihi kapatır. Reset Date to Weekly Hours: o gün için haftalık saatlere geri döner.
- Get Effective Hours for Date: `configuredHours` seçilen saatler, `workingHours` branch sınırları uygulanmış saatlerdir. Employee pasifse `workingHours` boştur.
- Bu sonuçlar randevu slotları değildir; mevcut randevuları çıkarma ve çakışma kontrolü appointment modülünde eklenecek.

### Working Hours klasör düzeni ve 404 kontrolü

- **Weekly Schedule:** haftalık saatleri gör, değiştir veya branch saatlerine geri dön.
- **Specific Date:** seçilen tarihin saatlerini gör, değiştir, günü kapat veya haftalık ayara geri dön.
- DELETE istekleri employee kaydını silmez; yalnız ilgili özel saat ayarını kaldırır.

Get Weekly Hours cevabında `configuredHours` employee özel saatlerini, `closedDays` kapatılan günleri, `inheritedDays` branch’ten alınan günleri gösterir. `workingHours` birleşik haftalık sonuçtur. Düzenlerken PUT için `configuredHours` ve `closedDays` kullan; birleşik listeyi geri gönderme.

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

## Uygun randevu başlangıçları

`Appointments → Availability → Get Available Slots` isteğini kullan.

1. Brand olarak giriş yap.
2. `employeeId`, `serviceId` ve `scheduleDate` değerlerini seç. Service employee'ye atanmış olmalı.
3. GET isteğini gönder; body gerekmez.
4. Cevaptaki `availableAppointmentSlotDetails.localTimes` başlangıç saatlerini içerir.

Saatler hizmet süresine göre üretilir; hizmetin tamamı çalışma aralığına sığmalıdır. Türkiye saatine göre en az bir saat sonrası listelenir. Kapalı/pasif employee veya geçmiş tarih için boş liste normaldir.
Bu endpoint henüz mevcut randevuları çıkarmaz; randevu oluşturmaz. Frontend'in rezervasyon garantisi olarak kullanabileceği son sürüm değildir.


Günlük öncelik: employee tarih kaydı → employee'nin o haftanın gününe özel saat/kapalı ayarı → branch'in o tarihteki saatleri. Tarih kaydındaki boş `workingHours` açıkça kapalı demektir; branch'e geri dönmez. Tarih kaydını DELETE ile kaldırınca bu sıra yeniden uygulanır.

V13: Daha önce boş haftalık planla bütün hafta kapatılmış employee'ler için yedi gün `closedDays` olarak korunur. Eski dolu planlarda eksik günler artık branch'ten alınır. Bu explicit kapatmaları kaldırmak için Reset to Branch Hours kullan.

## Randevu oluşturma — 12 Ekim 2026

- `Appointments → Brand Creation → Create Appointment as Brand`: brand login sonrası kullanılır. Geçmiş/yakın saatlere kayıt açabilir.
- `Appointments → Customer Creation → Create Appointment as Customer`: anonim denemek için logout yap, tekrar CSRF al. Cookie ile CSRF aynı oturuma ait olmalı. Başlangıç en az 1 saat sonrası olmalı.
- `branchId`, `employeeId`, `serviceId`, `scheduleDate`, `appointmentStartTime` değişkenlerini doldur. Çalışan ilgili şubeye, hizmet ilgili brand'e ait ve çalışana atanmış olmalı.
- `customerEmail` isteğe bağlıdır; alanı silerek deneyebilirsin. `source` gönderilmez; backend belirler. Başarılı cevap 201/PENDING olur, `appointmentId` kaydedilir; başlangıç/bitiş UTC olarak döner.
- Otomatik integration testi: anonim müşteri kaydı, brand kaydı, source/tarihlerin DB'de doğrulanması, boş email, yanlış şube, çalışma aralığı dışı, customer geçmiş tarih reddi, brand geçmiş tarih kabulü ve anonim panel erişimi reddi.
- Henüz OTP, abonelik/kabul kontrolü ve randevu çakışma engeli yok. Aynı saate tekrar POST göndermek şu an yeni kayıt oluşturabilir.
- Collection dosyasını Postman'e yeniden import et; açık uygulamadaki kopya kendiliğinden güncellenmez.
