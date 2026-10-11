# DEU Sakai API Specification (Verified)

## 1. Authentication
- **Method:** POST
- **URL:** `https://online.deu.edu.tr/portal/xlogin`
- **Headers:**
  - `Content-Type`: `application/x-www-form-urlencoded`
  - `Referer`: `https://online.deu.edu.tr/portal/xlogin`
  - `Origin`: `https://online.deu.edu.tr`
  - `User-Agent`: Modern Chromium UA
- **Payload:**
  - `eid`: <STUDENT_ID> (URL-encoded)
  - `pw`: <STUDENT_PASSWORD>
  - `submit`: `Giriş`
- **Success Criteria:** Set-Cookie headers capturing `SAKAI2SESSIONID` and `SAKAI_AFFINITY`.
- **Failure Indicator:** Body contains `Kullanıcı adı yada şifreniz hatalı` or status != 302/200.

## 2. Notification Polling
- **Method:** GET
- **URL:** `https://online.deu.edu.tr/api/users/me/notifications`
- **Headers:**
  - `Accept`: `application/json, text/plain, */*`
  - `Referer`: `https://online.deu.edu.tr/portal`
- **Response Format:** Array of notification objects.
- **Empty State:** `[]`

## 3. Storage & State
- Local persistence keeping an array of processed notification IDs.
- On initialization: fetch existing IDs without triggering alerts (Silent Bootstrap), then monitor for diffs.
