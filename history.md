# Photo Gateway — реализованная серверная часть

## Что сделано
- Разработан PHP-шлюз для пересылки фото между телефонами через интернет
- Шлюз работает как **очередь**: отправитель загружает фото → получатель забирает

## Эндпоинты API
| Действие | Запрос |
|----------|--------|
| Загрузить фото | `POST ?action=upload` (multipart, поле `photo`) |
| Список фото | `GET ?action=list` → JSON |
| Скачать фото | `GET ?action=get&id=xxx` → JPEG |
| Удалить фото | `GET ?action=delete&id=xxx` |
| Проверка связи | `GET ?action=ping` → `{"status":"ok"}` |

## Адрес
`https://attplus.in/album_gateway/index.php`

## Тестирование
- ✅ `ping` возвращает JSON
- ✅ Загрузка через curl работает
- ✅ Браузерная форма работает (после исправления `action="?action=upload"`)

```bash
`➜  album_gateway curl -A "Mozilla/5.0" http://attplus.in/album_gateway/index.php\?action\=ping
{"status":"ok","timestamp":1779031921}%                                                        ➜  album_gateway curl -X POST \
  -F "photo=@photo.jpg" \ 
  -A "Mozilla/5.0" \
  http://attplus.in/album_gateway/index.php\?action\=upload
{"status":"ok","id":"6a09e0a9b8e6d_1779032233.jpg"}
````

## Следующий шаг
Реализовать Kotlin-приложение для Android с двумя режимами:
- **Отправитель** — выбирает фото, отправляет на шлюз
- **Получатель (старый телефон)** — периодически проверяет `list`, скачивает новые фото и сохраняет в Google Фото с очисткой очереди

Сервер полностью готов.






---
## Дальний бэк-лог:

- доработать защиту PHP-шлюза (чтобы можно было настроить его отключение при странном поведении, удалить мусорную очередь и т.п.)
