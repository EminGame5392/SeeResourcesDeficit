# SeeResourcesDeficit - Плагин для ограничения ресурсов в Minecraft

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.16.5-brightgreen)
![License](https://img.shields.io/badge/License-MIT-blue)

Плагин SeeResourcesDeficit позволяет создавать дефицит определенных ресурсов на вашем сервере Minecraft. После достижения установленного лимита, игроки не смогут добывать или подбирать указанные предметы, а также будут получать визуальные эффекты и уведомления.

## 📦 Установка

1. Скачайте последнюю версию плагина из [Releases](https://github.com/your-repo/SeeResourcesDeficit/releases)
2. Поместите файл `.jar` в папку `plugins/` вашего сервера
3. Перезапустите сервер
4. Настройте конфигурационный файл по вашему усмотрению

## ⚙️ Конфигурация

Основной конфигурационный файл `config.yml` позволяет настроить:
- Предметы с дефицитом
- Лимиты для каждого предмета
- Эффекты при достижении лимита
- Сообщения и визуальные эффекты

Пример конфигурации:
```yaml
deficit_items:
  item1:
    material: diamond
    limit: 8000
    limit_end:
      VirtualEntity:
        enable: true
        types:
          - explosion
          - lightning
          - ParticleAnimation1
          - ParticleAnimation2
      Title:
        enable: true
        title: '&cДЕФИЦИТ'
        subtitle: '&cНа этот предмет закончился лимит'
      Message:
        enable: true
        message:
          - '&cДЕФИЦИТ &b>> &cЭтот предмет невозможно добыть из-за его дефицита!'
```

## 🎮 Команды

| Команда | Описание | Права |
|---------|----------|-------|
| `/seeresourcesdeficit reload` | Перезагружает конфигурацию | `seeresourcesdeficit.admin` |
| `/seeresourcesdeficit enable [item]` | Принудительно включает дефицит для предмета | `seeresourcesdeficit.admin` |
| `/seeresourcesdeficit disable [item]` | Принудительно выключает дефицит для предмета | `seeresourcesdeficit.admin` |

Алиас: `/seerd`

## ✨ Особенности

- Поддержка цветов через `&` и HEX-коды
- Настраиваемые лимиты для каждого предмета
- Различные типы уведомлений:
  - Титры (Title/SubTitle)
  - Сообщения в чат
  - BossBar
  - Визуальные эффекты (взрывы, молнии, частицы)
- Поддержка VirtualEntityAPI для расширенных эффектов
- Оптимизированная работа с памятью

## 📝 Зависимости

- [Spigot API 1.16.5](https://www.spigotmc.org/)
- [VirtualEntityAPI](https://github.com/by1337/VirtualEntityAPI) (опционально, для дополнительных эффектов)

## 📜 Лицензия

Этот проект распространяется под лицензией MIT. Подробнее см. в файле [LICENSE](LICENSE).

---

💡 **Совет**: Вы можете комбинировать различные эффекты для создания уникального опыта при достижении лимита ресурсов!

🔧 **Поддержка**: Если у вас возникли проблемы, то обращайтесь к [автору](http://gdev.seemine.su).
```
