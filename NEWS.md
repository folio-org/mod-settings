## 1.3.0
* Bump dependencies for Trillium (Vertx 5.0.10, vertx-lib 4.1.2). [MODSET-46](https://folio-org.atlassian.net/browse/MODSET-46)
* Improve Addresses endpoint: pageable, optional address field, better serialization, audit fields. [MODSET-42](https://folio-org.atlassian.net/browse/MODSET-42)
* Migrate FOLIO_HOST from mod-configuration to mod-settings (/base-url). [MODSET-40](https://folio-org.atlassian.net/browse/MODSET-40)
* Migrate tenant addresses from mod-configuration to mod-settings. [MODSET-37](https://folio-org.atlassian.net/browse/MODSET-37)
* Migrate from AbstractVerticle to VerticleBase. [MODSET-35](https://folio-org.atlassian.net/browse/MODSET-35)
* Upgrade to Vert.x 5.0. [MODSET-34](https://folio-org.atlassian.net/browse/MODSET-34)
* Depend on authtoken. [MODSET-31](https://folio-org.atlassian.net/browse/MODSET-31)
* Add GET+PUT /locale API and fix parsing of mod-configuration localeSettings. [MODSET-24](https://folio-org.atlassian.net/browse/MODSET-24)
* Don't run RestAssured on event-loop thread in LocaleServiceTest. [MODSET-36](https://folio-org.atlassian.net/browse/MODSET-36)
* Document Eureka requirements for permission names.

## 1.2.0
* Use correct permission names in doc/HOWTO.md. [MODSET-27](https://folio-org.atlassian.net/browse/MODSET-27)
* Enable workflow\_dispatch for api. #44
* Initial maven Workflow in-development. [FOLIO-4126](https://folio-org.atlassian.net/browse/FOLIO-4126)
* Update to Java 21. [MODSET-28](https://folio-org.atlassian.net/browse/MODSET-28)
* Make permissions desired more permissive for format. [MODSET-26](https://folio-org.atlassian.net/browse/MODSET-26)
* apk upgrade in Dockerfile. [MODSET-29](https://folio-org.atlassian.net/browse/MODSET-29)
* Update all dependencies for Sunflower (R1-2025). [MODSET-30](https://folio-org.atlassian.net/browse/MODSET-30)

## 1.1.0
* Upgrade dependencies (folio-vertx-lib, ...) for Ramsons. [MODSET-22](https://folio-org.atlassian.net/browse/MODSET-22)
* Add descriptor validator plugin. Change permission name to mod-settings.entries.put
* [MODSET-17: Use .withTransaction, Promise and better logging](https://folio-org.atlassian.net/browse/MODSET-17)
* Add new document [How to add new settings to the mod-settings module](doc/HOWTO.md). Fixes MODSET-18.
* Correctly document permission names. Fixes MODSET-27.

## 1.0.3
* [FOLIO-3944 Upgrade Actions for API-related Workflows](https://folio-org.atlassian.net/browse/FOLIO3-944)

## 1.0.2 2024-12-06
* Update library dependencies

## 1.0.1 2023-10-13
* Documentation updates

## 1.0.0 2023-02-16
