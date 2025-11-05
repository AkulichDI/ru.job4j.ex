// Пользовательское событие: "Сменить тип (сохранить статус) по параметру newType"

def obj = it
if (obj == null) return "Нет объекта"

// читаем параметр события (имя поля параметра — newType)
def TARGET_TYPE = params?.newType
if (!TARGET_TYPE) return "Не задан параметр newType"

// прежний статус
def oldStatus = (obj?.metaClass?.hasProperty(obj, 'status')) ? obj.status : null

// смена типа
try {
    obj.type = TARGET_TYPE
    obj.save()
} catch (ignored) {
    return "Не удалось сменить тип"
}

// возврат статуса
if (oldStatus != null && obj?.metaClass?.hasProperty(obj, 'status')) {
    try {
        // при несовместимости можно добавить соответствия статусов
        def statusToSet = oldStatus
        obj.status = statusToSet
        obj.save()
    } catch (ignored) { }
}

return "Готово"