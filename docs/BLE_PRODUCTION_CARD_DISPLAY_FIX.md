# BLE parsing → card display fix

Tanggal2 November2026. Status PARTIAL. Laporan ini mencatat delta setelah audit Q483; perubahan finalization sebelumnya tetap tersedia dan tidak direwrite.

## A. Alur production

Incoming telemetry tetap memakai parser/normalizer dan persistence existing. Received API membaca durable telemetry io_data, menggunakan shared EyeBeaconParser, resolve identitas terhadap registry tenant aktif, memilih decoder dari metadata profile yang terbukti, lalu mengirim Measurement/SensorData/SensorCapture/Source melalui DTO existing. Card menampilkan nilai packet yang sama; tidak graft dari fixture atau packet lain. Tidak menulis enrichment saat request GET.

## B. Profil yang tersedia

EYE_SENSOR_SHORT_V1 (9-byte capture eksplisit), EYE_SENSOR_FLAGS_V1, EYE_BEACON_FLAGS_V1, EYE_BEACON_BATTERY_V1, FULL_AD manufacturer9A08/version01. Sensor List11317 memiliki profile endian dan dedicated exact108xx slot memiliki verified identity. RAW9byte bukan bukti profil; battery satu byte juga perlu offset/field proof. [BTSMP1](https://wiki.teltonika-gps.com/view/BTSMP1), [FMC650 Beacon List](https://wiki.teltonika-gps.com/view/FMC650_Beacon_List).

## C. Registry

Resolver existing memakai active asset_ble_sensors tenant sama, explicitMAC dan asosiasi canonical terverifikasi/metadata capture. Ambiguity tetap unresolved. Captured ID tidak otomatis MAC. Tidak mengubah BLE Register/Wasted atau schema. Test service menggunakan receiver TEST_RECEIVER dan asosiasi test eksplisit; bukan konfigurasi live. Mapping fisik SENSOR1 dan capture offset/size belum diberi bukti tambahan oleh pengguna. Tidak mengaktifkan profil berdasarkan panjang.

## D. Decoding

FixtureQ483 real menghasilkan3blok. Under explicit short profile: SENSOR2 temperature31.82°C,humidity72%RH,movementfalse,counter2212,pitch1°,roll-2°,battery3030mV; sensor lain31.72°C,79%RH,false,4791,0°,-1°,3110mV. Explicit battery-byte test menghasilkan3100mV dari6E. Semua hasil tersebut conditional profile evidence, bukan klaim live verified. Tanpa profil, service menahan sensor values dan battery sambil menyimpan RAW/pendingreason. Format parameterized menggunakan constant01, bukan count; legacy ditangani terpisah.

## E. Source AVL

Source.ioId548 diteruskan ke kedua card bersama ioIds bila ada.10500Simple dan548Advanced mengikuti FMC650;385ManualCAN pada FMC650/ALELSHub tidak dianggap BLE lintasmodel. CodecALELSJSON yang tidak diberikan tetap NotProvided/Unknown. RSSI asli, Generic distance, timestamps, receiver GPS tidak diubah. Tidak ada calculated geographic location.

## F. Perbandingan API

Tidak ada perubahan kontrak/backend production pada delta ini. Sebelum dan sesudah, API mempunyai measurement.temperatureCelsius,sensorData.*,batteryMillivolts,sensorCapture.reason,source.ioId,sensor.type/verifiedMac. Offline service→DTO check membuktikan3records, registry resolution dan semua field. Tanpa profile sensorData tetapnull; dengan attestation test fields terisi. Authenticated HTTP API before/after belum dapat diperoleh, sehingga tidak ada response live yang dikarang.

## G. EyeBeacon sebelum/sesudah

Sebelum: battery kosong tanpa alasan field-level; conditional temperature branch dapat menampilkan sensor-only temperature pada EyeBeacon. Sesudah: Battery Voltage mV tetap dari measurement.batteryMillivolts; bila absen, alasan capture/error atau absentverifiedfield terlihat. Sensor-only temperature dihapus dari branchEyeBeacon. Tidak ada penambahan layout/CSS. Battery terverifikasi tampil, sensor-onlyhumidity/temperature tidak masuk cardBeacon.

## H. EyeSensor sebelum/sesudah

Sebelum: profileSHORT menyebabkan ProtocolBLE tampil EYESensor walau protokol sebenarnya Custom/tidak diketahui; humidity hanya%,movementfalseStationary, beberapa unavailable fields tanpa alasan. Sesudah: protokol hanya dari identity/advertisement, shortcapture tidak mengganti protokol; humidity%RH,movementfalseNotMoving; temperature/humidity/counter/pitch/roll/battery mempunyai alasan saat tidak tersedia, termasuk fieldErrors. Zero danfalse tetap nilai valid. MagnetStatus hanya tampil jika encoded. Existing field bindings untuk valid values tetap dipakai, tidak mengganti dengan hasil fixture.

## I. Exact production delta

1. `D:/alels-platform-work/web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/utils/eyebeacon-presentation.ts` — hapus inferensi protocol dari sensor capture/type, gunakan identity/validatedadvertisement.
2. `D:/alels-platform-work/web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/components/eyebeacon-panel.tsx` — alasan unavailable yang benar untuk kedua card, unit/description,movementlabel,conditionalmagnet dan cegah sensor-onlytemperature padaBeacon. Existing layout/styling/navigation/expansion tetap.

Tidak menambah decoder,profiledefault atau association produksi karena belum ada bukti konfigurasi baru.

## J. Cleanup

Temporary Java service/profile regression,protocol regression,SSR script/bundle,JSONfixtures dan compiled checkerclasses dihapus setelah PASS. Manifest [artifacts](BLE_CARD_DISPLAY_FIX_DELETED_ARTIFACTS.csv).18 BLE-only source testfiles dari tahap sebelumnya tetap terhapus sesuai instruksi; manifest sebelumnya [finalization](BLE_FINALIZATION_DELETED_FILES.csv). Shared JUnit/Spring,Codecgoldenframe/CRC,dictionarytests,productionparser dan dokumentasi dipertahankan. Penghapusan dedicatedBLEtests mengurangi automatedregressioncoverage masa depan; hasil temporarychecks bukan suite permanen.

## K. Hasil verifikasi

- Backend mvn test:46tests,0failure,0error,0skip,BUILDSUCCESS.
- Gateway TeltonikaCodec8EParserTest + DictionaryRegistryResolverTest:3tests,0failure/error/skip,BUILDSUCCESS; goldenframe danCRC tetaplulus.
- Temporary protocolcheck PASS:385/10500simple,548parameterized/legacy,10828/10829/10831,11317,truncationdiagnostic,1/20/50/100records dan1024bytesafetyguard.
- Temporary actual service→DTO PASS:3blocks,configuredregistryassociations,profilegating,pendingwithoutprofile,expectedmeasurements,source548,RSSI/distance,originalGPS. Test-onlyrepositoryinputs, bukan database/runtimeauthenticatedtest.
- Temporary actual React BeaconCard SSR PASS:temperature/humidity/movement/counter/pitch/roll/batteryunits,zero/false,pendingreasons,protocolCustom,Codecnotsupplied,typefilter/dedup(oneBeacon/twoSensors),noSensorfieldsinBeacon. Details markup dirender hidden sesuai defaultcollapsed; belum browser click/visualQA.
- Frontend npm run build:PASS,includesTypeScripttypecheck; existing>500kBchunkwarning.
- git diff --check: dijalankan sebagai finalcheck.

## L. Runtime evidence/blocker

Actual endpoint http://127.0.0.1:8080/api/ble/gateways/4/received-beacons returns403 withoutauthorizedsession. Shell tidak mempunyai existing authenticatedsession; tidak membuat JWT,tidak bypassauthorization,tidak restartbackend. Deploymentbuildbaru dan browseractualcard belumverified. Q483 RAWpersistence sebelumnya dibuktikan telemetry9408/raw13062; sourceGPS/timestamps tercatat [audit](BLE_Q483_READ_ONLY_AUDIT.md). Decodedhistorytables tidak tersedia pada audit lokal sebelumnya; tidak membuat migration atau mengubah persistence.

## M. Stability

Delta ini hanya2frontendBLE-specificfiles dan docs/generatedtemporaryverification. Tidak mengubah TCP/ACK,Codecframing,JSONtransport,firmware,ingestion,Kafka/DirectDB,sourceidentity/dedup,persistence,register/Wasted,migrations/history,globalUI/UX atau tabordering. Tidak menambahkanhardcodedfixtures/IMEI ke productionsource. Profil live tetap perlu actualverifiedmetadata.

## N. Acceptance

PARTIAL: safe bindingcorrections dan seluruh offlinechecks di atas PASS. Liveprofileapplicability, verifiedassociationconfiguration,authenticatedAPI→actualbrowsercard dan deploymentbaru belum dibuktikan. Tidak mengklaim semua realobservations sudah tampil pada instalasi aktif. Kontrak konfigurasi existing ada di [capture configuration](BLE_PRODUCTION_CAPTURE_CONFIGURATION.md).
