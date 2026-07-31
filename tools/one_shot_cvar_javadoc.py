#!/usr/bin/env python3
"""One-shot documentation patch for scoped C-variable projection semantics."""

from pathlib import Path


def patch_dictionary() -> None:
    path = Path("kanger/src/org/kanger/factory/DictionaryFactory.java")
    source = path.read_text(encoding="utf-8")
    anchor = (
        "    public ITerm createCVar(IRule r, ITerm name, ITerm parent) "
        "throws Exception {\n"
    )
    marker = "Отдельная identity здесь семантически обязательна"

    if source.count(anchor) != 1:
        raise RuntimeError("Unexpected DictionaryFactory.createCVar anchor count")
    if marker in source:
        raise RuntimeError("Scoped C-variable Javadoc is already present")

    javadoc = """    /**
     * Создаёт канонический descriptor C-переменной, принадлежащий правилу.
     *
     * <p>При {@code parent == null} создаётся корневая C-переменная
     * {@code %N}. При ненулевом {@code parent} создаётся временная scoped-
     * проекция {@code *N} существующей C-переменной в контекст другого
     * правила во время унификации {@link org.kanger.Linker}. Отдельная identity
     * здесь семантически обязательна: прямое повторное использование исходного
     * {@link Term} пересекло бы независимые области связывания правил и изменило
     * бы результаты кванторного и реляционного вывода.</p>
     *
     * <p>Связь parent/child публикуется активным {@link Mind} через
     * {@code linkCVar(...)} только как runtime adjacency. Она не является
     * сохраняемым знанием и удаляется вместе с любым из связанных объектов.
     * {@code Linker} использует эту связь, чтобы распознавать уже выполненную
     * scoped-проекцию и не строить рекурсивную цепочку искусственных
     * C-переменных.</p>
     *
     * @param r правило, которому принадлежит создаваемый descriptor
     * @param name исходное имя переменной для отображения и диагностики
     * @param parent исходная C-переменная для scoped-проекции либо
     *               {@code null} для корневой C-переменной
     * @return канонический {@link Term} корневой либо scoped C-переменной
     * @throws Exception если не удалось канонизировать descriptor, выделить
     *                   идентификатор или опубликовать runtime adjacency
     */
"""

    path.write_text(source.replace(anchor, javadoc + anchor), encoding="utf-8")


def patch_linker() -> None:
    path = Path("kanger/src/org/kanger/Linker.java")
    source = path.read_text(encoding="utf-8")
    anchor = (
        "                                                    if (tm.isCVariable() "
        "&& tm.getParentId(mind) == -1 &&"
    )
    marker = "Переход через границу Rule требует отдельной scoped identity"

    if source.count(anchor) != 2:
        raise RuntimeError("Unexpected Linker scoped-projection anchor count")
    if marker in source:
        raise RuntimeError("Scoped C-variable Linker comments are already present")

    comment = """                                                    /*
                                                     * Переход через границу Rule требует отдельной
                                                     * scoped identity C-переменной. Прямое использование
                                                     * tm объединило бы области связывания исходного и
                                                     * принимающего правил. Контракт описан в
                                                     * DictionaryFactory.createCVar(...).
                                                     */
"""

    path.write_text(source.replace(anchor, comment + anchor), encoding="utf-8")


def main() -> None:
    patch_dictionary()
    patch_linker()


if __name__ == "__main__":
    main()
