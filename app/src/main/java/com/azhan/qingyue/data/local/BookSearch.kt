package com.azhan.qingyue.data.local

/**
 * 书籍搜索的匹配规则：书名 / 文件名 / 作者，任一包含关键词即算命中。
 *
 * 单独提出来是为了**能被直接测试** —— 匹配规则是搜索里最容易悄悄改坏的部分
 * （比如漏了 `lowercase()`，英文关键词就永远搜不到；或者漏了某个字段，
 * 用作者名就搜不出来），而这类问题不会编译报错。
 *
 * 大小写归一和空串判断都在**函数内部**做，不依赖调用方遵守约定。
 * 最初的设计是「要求调用方先 trim + lowercase」，结果我自己写测试时就传了
 * 大写关键词 —— 这种约定式接口迟早有人忘，而忘了不报错、只是搜不到。
 * 每本书多几次 `lowercase()` 的开销可以忽略（书架规模是几百本量级）。
 *
 * @return 关键词为空时一律返回 false，调用方不必特判。
 */
internal fun BookEntity.matchesSearch(keyword: String): Boolean {
    val k = keyword.trim().lowercase()
    if (k.isEmpty()) return false

    return title.lowercase().contains(k) ||
        fileName.lowercase().contains(k) ||
        author?.lowercase()?.contains(k) == true
}
