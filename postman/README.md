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
- Bu sonuçlar randevu slotları değildir; bunlar çalışma saatleridir; appointment endpoint’i PENDING/APPROVED kayıtları çıkarır.

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
Bu endpoint PENDING/APPROVED randevuları çıkarır; saati rezerve etmez. Seçim sonrası create isteği ayrıca çakışma kontrolü yapar.


Günlük öncelik: employee tarih kaydı → employee'nin o haftanın gününe özel saat/kapalı ayarı → branch'in o tarihteki saatleri. Tarih kaydındaki boş `workingHours` açıkça kapalı demektir; branch'e geri dönmez. Tarih kaydını DELETE ile kaldırınca bu sıra yeniden uygulanır.

V13: Daha önce boş haftalık planla bütün hafta kapatılmış employee'ler için yedi gün `closedDays` olarak korunur. Eski dolu planlarda eksik günler artık branch'ten alınır. Bu explicit kapatmaları kaldırmak için Reset to Branch Hours kullan.

## Randevu oluşturma — 12 Ekim 2026

- `Appointments → Brand Creation → Create Appointment as Brand`: brand login sonrası kullanılır. Geçmiş/yakın saatlere kayıt açabilir.
- `Appointments → Customer Creation → Create Appointment as Customer`: anonim denemek için logout yap, tekrar CSRF al. Cookie ile CSRF aynı oturuma ait olmalı. Başlangıç en az 1 saat sonrası olmalı.
- `branchId`, `employeeId`, `serviceId`, `scheduleDate`, `appointmentStartTime` değişkenlerini doldur. Çalışan ilgili şubeye, hizmet ilgili brand'e ait ve çalışana atanmış olmalı.
- `customerEmail` isteğe bağlıdır; alanı silerek deneyebilirsin. `source` gönderilmez; backend belirler. Başarılı cevap 201/PENDING olur, `appointmentId` kaydedilir; başlangıç/bitiş UTC olarak döner.
- Otomatik integration testi: anonim müşteri kaydı, brand kaydı, source/tarihlerin DB'de doğrulanması, boş email, yanlış şube, çalışma aralığı dışı, customer geçmiş tarih reddi, brand geçmiş tarih kabulü ve anonim panel erişimi reddi.
- Müşteri OTP kontrolü uygulanıyor; abonelik ve şube kabul kontrolü uygulanıyor. Aynı çalışana çakışan saat için tekrar POST göndermek 409 döndürür; eşzamanlı istekler V17 veritabanı constraint’iyle korunur.
- Collection dosyasını Postman'e yeniden import et; açık uygulamadaki kopya kendiliğinden güncellenmez.

## Yeni kayıt izni

Her iki create isteğinden önce admin hesabıyla brand hesabının `subscriptionActive` alanını true yap; ardından brand hesabıyla `Branches → Open Appointment Acceptance` isteğini çalıştır. Admin/brand login değişikliklerinden sonra CSRF token'ı yenile.

Abonelik pasifse veya şube kabulü kapalıysa create 409 döner ve nedenini detail alanında açıklar. İkisi de açıkken diğer doğrulamalar uygunsa 201 döner. Bu ayarlar panel girişini engellemez ve mevcut randevuları silmez. Müşteri için anonimliğini koruyup yeniden CSRF al; aşağıdaki OTP adımlarını uygula.

## Customer OTP kullanımı

`Appointments → Customer Creation` klasöründe sırasıyla:

1. `01 Request SMS Code`: customerPhone değişkenini doldur. Dev backend logundaki `DEV SMS` satırından kodu al ve smsCode değişkenine yaz.
2. `02 Verify SMS Code`: doğrulanınca phoneVerificationToken otomatik kaydedilir.
3. `Create Appointment as Customer`: aynı telefon ve X-Phone-Verification header'ı gönderilir. Başarılı kayıtta token tüketilir; yeni randevu için yeniden doğrulama gerekir. Başarısız randevuda token süresi dolmadıysa yeniden denenebilir.

Kod 2 dakika geçerlidir; 3 yanlış denemeden sonra kullanılamaz. Yeni kod için 60 saniye beklenir; son bir saat içinde en fazla 3 gönderim yapılır. 429 cevabındaki Retry-After bekleme süresidir. Doğrulama token'ı 5 dakika geçerlidir. Kod veya token başka telefonda kullanılamaz. Brand Creation OTP istemez. Gerçek SMS sağlayıcısı bağlı değildir; sadece dev profilinde log üzerinden denenir, prod profilinde gönderim 503 döner.

## Randevu onaylama ve reddetme

`Appointments → Appointment Actions` altında `Approve Appointment` ve `Reject Appointment` var. Randevunun sahibi brand ile giriş yapıp CSRF yenile. `appointmentId` oluşturma cevabından otomatik alınır; body gönderilmez. Başarı 200 ve boş body döner.

İki işlemi denemek için ayrı PENDING randevular kullan. Aynı ID ile `Again (409)` isteği durum değişikliğinin tekrar yapılamadığını kontrol eder. Başka brand'in randevusu veya bulunmayan ID 404 döner. REJECTED kayıt silinmez, saati serbest kalır. V19 migration uygulanmış olmalı. Güncel collection dosyasını yeniden import et.

## Brand appointment actions — 17 October 2026

Postman: `Appointments → Appointment Actions`. POST panel endpoints: `/api/panel/appointment/approve-appointment/{appointmentId}`, `/reject-appointment/{appointmentId}` and `/cancel-appointment/{appointmentId}` (same prefix). Use the owning BRAND session and CSRF, with no body. Success returns 200 with an empty body.

Approval/rejection require PENDING. Cancellation accepts PENDING or APPROVED and sets CANCELLED. Missing/foreign records return 404; invalid/repeated transitions return 409. A row lock serializes concurrent decisions. Cancelled/rejected records remain stored and no longer block time; retrying cancellation cannot affect a replacement booking. Cancellation does not require an active subscription or branch acceptance. V19 permits REJECTED and V20 permits CANCELLED; V17 still blocks only PENDING/APPROVED.

Customer cancellation and brand rejection/cancellation SMS enqueueing are implemented. Cancellation cutoff snapshots are implemented in V21. Reimport the collection to see new requests.

## Brand cancellation policy — 17 October 2026

`PATCH /api/panel/brand/cancellation-policy` accepts `{"cancellationNoticeHours":3}` for the authenticated BRAND. CSRF required. Integer hours 0–168; default 0 means until the appointment starts. Missing/null/out-of-range values return 400. The 200 response and GET `/api/panel/brand` include the setting. Postman: Brand → Update Cancellation Policy.

Both brand and customer creation store and return `cancellationDeadline = startsAt - cancellationNoticeHours` as an Instant. Changing the policy affects only new bookings. V21 adds the setting and backfills existing bookings with their start time (initial zero-hour policy). Brand cancellation is not limited by this deadline. Customer token access and deadline enforcement are implemented as described below.

## Customer access and cancellation — 22 October 2026

Postman: `Appointments → Customer Access and Cancellation`. Set `appointmentAccessToken` to the raw token from the SMS link (dev: message in DEV SMS logs). It is not the OTP proof or token hash.

- GET `/api/public/appointment/details` with `X-Appointment-Token` displays details without changing the booking.
- POST `/api/public/appointment/cancel-appointment` with the same header and a valid CSRF cookie/header cancels without login or body.
- Cancellation locks the appointment by token hash. Expired access token returns 410; status other than PENDING/APPROVED or reaching the stored cancellation deadline returns 409; unknown token returns 404. Equality with the deadline is rejected.
- Cancellation stores CANCELLED and one encrypted APPOINTMENT_CANCELLED SMS job atomically; an enqueue failure rolls back cancellation. Repeated/concurrent cancellation cannot enqueue a second SMS. Cancelled bookings no longer block time. Cancellation SMS is not automatically retried.
- Manual flow: Get Details → Cancel Appointment as Customer → Cancel Again (409). Get Details again shows CANCELLED while the access token is valid. New collection requests require reimporting the JSON file.

### Brand decision SMS — 24 October 2026

Brand rejection and cancellation enqueue APPOINTMENT_REJECTED / APPOINTMENT_CANCELLED in the same transaction as the status change. Failed queue persistence rolls back both changes. Repeated/invalid decisions return 409 without another SMS; foreign appointments return 404. Concurrent decisions produce only the winning decision's SMS. Delivery failure has no automatic retry for these message types.

In Postman, use Appointments → Appointment Actions. Test Reject Appointment → Reject Appointment Again (409) on one PENDING booking, then Cancel Appointment → Cancel Appointment Again (409) on a separate PENDING or APPROVED booking. Keep appointmentId unchanged for each repeat. HTTP checks verify responses; database-backed integration tests verify encrypted SMS persistence, transaction rollback and concurrency. A 200 response does not mean the SMS provider has delivered the message.
