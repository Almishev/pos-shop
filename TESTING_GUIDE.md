# Ръководство за тестове - CategoryService

Този документ описва тестовата стратегия и инструкции за изпълнение на тестовете за CategoryService в Billing Software проекта.

## Структура на тестовете

### 1. Unit тестове

#### CategoryServiceTest.java
- **Цел**: Тестване на бизнес логиката на CategoryService
- **Технологии**: JUnit 5, Mockito
- **Покритие**: 
  - Добавяне на категория
  - Четене на всички категории
  - Изтриване на категория
  - Обработка на грешки

#### FileUploadServiceTest.java
- **Цел**: Тестване на файловия upload сервиз
- **Покритие**:
  - Upload на файлове
  - Изтриване на файлове
  - Обработка на грешки

### 2. Интеграционни тестове

#### CategoryServiceIntegrationTest.java
- **Цел**: Тестване на интеграцията между компонентите
- **Технологии**: @SpringBootTest, H2 Database
- **Покритие**:
  - Интеграция с базата данни
  - End-to-end сценарии
  - Валидация на данни

#### CategoryRepositoryTest.java
- **Цел**: Тестване на repository слоя
- **Технологии**: @DataJpaTest, H2 Database
- **Покритие**:
  - CRUD операции
  - Database constraints
  - Query методи

### 3. Controller тестове

#### CategoryControllerTest.java
- **Цел**: Тестване на REST API endpoints
- **Технологии**: @WebMvcTest, MockMvc
- **Покритие**:
  - HTTP endpoints
  - Request/Response валидация
  - Error handling

## Конфигурация

### Тестова база данни
- Използва се H2 in-memory база данни
- Конфигурация в `application-test.properties`
- Автоматично създаване и изтриване на схемата

### Зависимости
```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

## Изпълнение на тестовете

### Изпълнение на всички тестове
```bash
mvn test
```

### Изпълнение на конкретен тест клас
```bash
mvn test -Dtest=CategoryServiceTest
```

### Изпълнение на конкретен тест метод
```bash
mvn test -Dtest=CategoryServiceTest#add_ShouldAddNewCategory_Successfully
```

### Изпълнение на тестове с покритие
```bash
mvn test jacoco:report
```

## Тестови сценарии

### 1. Добавяне на категория
- ✅ Успешно добавяне с валидни данни
- ❌ Грешка при празно име
- ❌ Грешка при null стойности
- ❌ Грешка при дублиране на име
- ❌ Грешка при проблем с файл upload

### 2. Четене на категории
- ✅ Връщане на всички категории
- ✅ Връщане на празен списък
- ✅ Правилно броене на продукти

### 3. Изтриване на категория
- ✅ Успешно изтриване
- ❌ Грешка при несъществуваща категория
- ❌ Грешка при null/празен ID

### 4. Валидация на данни
- ✅ Уникалност на categoryId
- ✅ Уникалност на name
- ✅ Задължителни полета

## Best Practices

### 1. Именуване на тестове
```java
@Test
@DisplayName("Should successfully add a new category")
void add_ShouldAddNewCategory_Successfully() {
    // test implementation
}
```

### 2. Arrange-Act-Assert pattern
```java
@Test
void testMethod() {
    // Arrange - подготовка на данни
    CategoryRequest request = createTestRequest();
    
    // Act - изпълнение на тествания код
    CategoryResponse result = categoryService.add(request, file);
    
    // Assert - проверка на резултата
    assertNotNull(result);
    assertEquals(expectedName, result.getName());
}
```

### 3. Mocking
```java
@Mock
private CategoryRepository categoryRepository;

@InjectMocks
private CategoryServiceImpl categoryService;
```

### 4. Test isolation
```java
@BeforeEach
void setUp() {
    // Подготовка за всеки тест
}

@AfterEach
void tearDown() {
    // Почистване след всеки тест
}
```

## Покритие на кода

### Цели за покритие
- **Line Coverage**: > 90%
- **Branch Coverage**: > 85%
- **Method Coverage**: > 95%

### Генериране на отчет за покритие
```bash
mvn clean test jacoco:report
```

Отчетът се генерира в: `target/site/jacoco/index.html`

## Debugging на тестове

### 1. Логване
```properties
logging.level.in.bushansirgur.billingsoftware=DEBUG
logging.level.org.springframework.web=DEBUG
```

### 2. H2 Console
- URL: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:testdb`
- Username: `sa`
- Password: (празен)

### 3. IDE Debugging
- Поставяне на breakpoints в тестовете
- Изпълнение в debug режим
- Инспектиране на променливи

## Troubleshooting

### Често срещани проблеми

1. **H2 Database не се стартира**
   - Проверете `application-test.properties`
   - Уверете се, че H2 зависимостта е добавена

2. **Mock не работи**
   - Проверете `@Mock` и `@InjectMocks` анотациите
   - Уверете се, че използвате правилните import-и

3. **Тестове не се изпълняват**
   - Проверете Java версията (21)
   - Уверете се, че Maven е правилно конфигуриран

4. **Database constraints грешки**
   - Проверете уникалните ограничения
   - Уверете се, че тестовете се изчистват правилно

## Разширяване на тестовете

### Добавяне на нови тестове
1. Създайте нов тест клас
2. Следвайте именуването конвенциите
3. Добавете `@DisplayName` анотации
4. Използвайте AAA pattern

### Добавяне на нови сценарии
1. Идентифицирайте edge cases
2. Добавете тестове за error handling
3. Тествайте boundary conditions
4. Добавете performance тестове при нужда

## Заключение

Този тестов набор осигурява:
- ✅ Високо покритие на кода
- ✅ Надеждност на функционалността
- ✅ Лесна поддръжка
- ✅ Документирана логика
- ✅ Автоматизирано тестване

За въпроси или проблеми, моля свържете се с development екипа.
