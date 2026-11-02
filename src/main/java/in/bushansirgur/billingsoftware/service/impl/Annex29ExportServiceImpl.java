package in.bushansirgur.billingsoftware.service.impl;

import in.bushansirgur.billingsoftware.entity.CustomerEntity;
import in.bushansirgur.billingsoftware.entity.DeliveryEntity;
import in.bushansirgur.billingsoftware.entity.DeliveryLineEntity;
import in.bushansirgur.billingsoftware.entity.FiscalDeviceEntity;
import in.bushansirgur.billingsoftware.entity.ItemEntity;
import in.bushansirgur.billingsoftware.entity.OrderEntity;
import in.bushansirgur.billingsoftware.entity.OrderItemEntity;
import in.bushansirgur.billingsoftware.entity.StockMovementEntity;
import in.bushansirgur.billingsoftware.entity.UserEntity;
import in.bushansirgur.billingsoftware.entity.UserRolePeriodEntity;
import in.bushansirgur.billingsoftware.io.Annex29TableResponse;
import in.bushansirgur.billingsoftware.io.AuditAction;
import in.bushansirgur.billingsoftware.io.OrderStatus;
import in.bushansirgur.billingsoftware.repository.CustomerRepository;
import in.bushansirgur.billingsoftware.repository.DeliveryRepository;
import in.bushansirgur.billingsoftware.repository.FiscalDeviceRepository;
import in.bushansirgur.billingsoftware.repository.ItemRepository;
import in.bushansirgur.billingsoftware.repository.OrderEntityRepository;
import in.bushansirgur.billingsoftware.repository.StockMovementRepository;
import in.bushansirgur.billingsoftware.repository.UserRepository;
import in.bushansirgur.billingsoftware.repository.UserRolePeriodRepository;
import in.bushansirgur.billingsoftware.service.Annex29ExportService;
import in.bushansirgur.billingsoftware.util.OperatorNames;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class Annex29ExportServiceImpl implements Annex29ExportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final OrderEntityRepository orderRepository;
    private final DeliveryRepository deliveryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final UserRolePeriodRepository userRolePeriodRepository;
    private final FiscalDeviceRepository fiscalDeviceRepository;
    private final CustomerRepository customerRepository;
    private final String shopCode;
    private final String shopName;
    private final LocalDate configuredAt;

    public Annex29ExportServiceImpl(OrderEntityRepository orderRepository,
                                     DeliveryRepository deliveryRepository,
                                     StockMovementRepository stockMovementRepository,
                                     ItemRepository itemRepository,
                                     UserRepository userRepository,
                                     UserRolePeriodRepository userRolePeriodRepository,
                                     FiscalDeviceRepository fiscalDeviceRepository,
                                     CustomerRepository customerRepository,
                                     @Value("${shop.code:SHOP0001}") String shopCode,
                                     @Value("${shop.name:Магазин}") String shopName,
                                     @Value("${shop.configuredAt:2026-01-01}") String configuredAt) {
        this.orderRepository = orderRepository;
        this.deliveryRepository = deliveryRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
        this.userRolePeriodRepository = userRolePeriodRepository;
        this.fiscalDeviceRepository = fiscalDeviceRepository;
        this.customerRepository = customerRepository;
        this.shopCode = shopCode;
        this.shopName = shopName;
        LocalDate parsed;
        try {
            parsed = LocalDate.parse(configuredAt);
        } catch (Exception ex) {
            parsed = LocalDate.of(2026, 1, 1);
        }
        this.configuredAt = parsed;
    }

    @Override
    public Annex29TableResponse table(String code, LocalDate from, LocalDate to, String device, String operatorCode) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Периодът е невалиден");
        }
        String table = code == null ? "" : code.trim();
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.atTime(23, 59, 59);
        List<OrderEntity> orders = orderRepository.findAllByCreatedAtBetweenOrderByCreatedAtAsc(start, end).stream()
                .filter(order -> matches(order, device, operatorCode))
                .toList();
        return switch (table) {
            case "18.1" -> salesSummary(orders);
            case "18.2" -> payments(orders);
            case "18.3" -> saleLines(orders);
            case "18.4" -> stornoLines(orders);
            case "18.5" -> voidedLines(orders);
            case "18.6" -> deliverySummary(from, to, operatorCode);
            case "18.7" -> deliveryLines(from, to, operatorCode);
            case "18.8" -> stockMovement(from, to);
            case "18.9.operators" -> operators();
            case "18.9.roles" -> roles();
            case "18.9.rights" -> rights();
            case "18.9.payments" -> paymentTypes();
            case "18.9.actions" -> actionTypes();
            case "18.9.sites" -> sites();
            case "18.9.workplaces" -> workplaces();
            case "18.9.customers" -> customers();
            case "18.9.suppliers" -> suppliers();
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Непозната таблица");
        };
    }

    @Override
    public byte[] csv(Annex29TableResponse table) {
        StringBuilder sb = new StringBuilder();
        sb.append(table.getColumns().stream().map(this::cell).collect(Collectors.joining(";"))).append('\n');
        for (List<String> row : table.getRows()) {
            sb.append(row.stream().map(this::cell).collect(Collectors.joining(";"))).append('\n');
        }
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] file = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, file, 0, bom.length);
        System.arraycopy(body, 0, file, bom.length, body.length);
        return file;
    }

    private Annex29TableResponse salesSummary(List<OrderEntity> orders) {
        List<List<String>> rows = new ArrayList<>();
        for (OrderEntity order : originals(orders)) {
            double gross = amount(order.getGrandTotal());
            double vat = amount(order.getTax());
            rows.add(List.of(
                    text(order.getUniqueSaleNumber()),
                    text(order.getOrderId()),
                    shopCode,
                    shopName,
                    date(order.getOpenedAt(), order.getCreatedAt()),
                    time(order.getOpenedAt(), order.getCreatedAt()),
                    text(order.getFiscalIndividualNumber()),
                    text(order.getOperatorCode()),
                    money(gross - vat),
                    money(0),
                    money(vat),
                    money(gross),
                    "",
                    "",
                    date(order.getClosedAt(), order.getCreatedAt()),
                    time(order.getClosedAt(), order.getCreatedAt()),
                    "",
                    text(order.getCustomerName())
            ));
        }
        return table("18.1", "Обобщени данни за продажбите", List.of(
                "УНП", "Системен номер", "Код на обект", "Наименование на обект",
                "Дата на откриване", "Час на откриване", "Код на работно място", "Код на оператор",
                "Обща сума без ДДС, евро", "Отстъпка, евро", "ДДС, евро", "Дължима сума, евро",
                "Фактура номер", "Фактура дата", "Дата на приключване", "Час на приключване",
                "Клиент код", "Клиент име"
        ), rows);
    }

    private Annex29TableResponse payments(List<OrderEntity> orders) {
        List<List<String>> rows = new ArrayList<>();
        for (OrderEntity order : originals(orders)) {
            if (order.getStatus() == OrderStatus.VOIDED) {
                continue;
            }
            double gross = amount(order.getGrandTotal());
            double vat = amount(order.getTax());
            rows.add(List.of(
                    text(order.getUniqueSaleNumber()),
                    text(order.getOrderId()),
                    date(order.getOpenedAt(), order.getCreatedAt()),
                    date(order.getClosedAt(), order.getCreatedAt()),
                    money(gross),
                    date(order.getClosedAt(), order.getCreatedAt()),
                    text(order.getOperatorCode()),
                    money(gross - vat),
                    money(vat),
                    order.getPaymentMethod() == null ? "" : order.getPaymentMethod().name(),
                    text(order.getFiscalIndividualNumber())
            ));
        }
        return table("18.2", "Плащания по продажбите", List.of(
                "УНП", "Системен номер", "Дата на откриване", "Дата на приключване",
                "Обща сума, евро", "Дата на плащане", "Код на оператор",
                "Платена сума без ДДС, евро", "ДДС, евро", "Вид плащане", "Индивидуален номер на ФУ"
        ), rows);
    }

    private Annex29TableResponse saleLines(List<OrderEntity> orders) {
        List<List<String>> rows = new ArrayList<>();
        for (OrderEntity order : originals(orders)) {
            if (order.getStatus() == OrderStatus.VOIDED || order.getItems() == null) {
                continue;
            }
            for (OrderItemEntity line : order.getItems()) {
                rows.add(lineRow(order, line));
            }
        }
        return table("18.3", "Детайлни данни за продажбите", lineColumns(), rows);
    }

    private Annex29TableResponse stornoLines(List<OrderEntity> orders) {
        List<List<String>> rows = new ArrayList<>();
        for (OrderEntity order : orders) {
            if (order.getOriginalOrderId() == null || order.getItems() == null) {
                continue;
            }
            for (OrderItemEntity line : order.getItems()) {
                List<String> row = new ArrayList<>(lineRow(order, line));
                row.add(date(order.getClosedAt(), order.getCreatedAt()));
                row.add(time(order.getClosedAt(), order.getCreatedAt()));
                row.add(date(order.getCreatedAt(), order.getCreatedAt()));
                row.add(time(order.getCreatedAt(), order.getCreatedAt()));
                row.add(text(order.getFiscalIndividualNumber()));
                row.add(text(order.getOperatorCode()));
                rows.add(row);
            }
        }
        List<String> columns = new ArrayList<>(lineColumns());
        columns.addAll(List.of(
                "Дата на приключване", "Час на приключване",
                "Дата на сторниране", "Час на сторниране",
                "Индивидуален номер на ФУ", "Код на оператор"
        ));
        return table("18.4", "Сторнирани продажби", columns, rows);
    }

    private Annex29TableResponse voidedLines(List<OrderEntity> orders) {
        List<List<String>> rows = new ArrayList<>();
        for (OrderEntity order : originals(orders)) {
            if (order.getStatus() != OrderStatus.VOIDED || order.getItems() == null) {
                continue;
            }
            for (OrderItemEntity line : order.getItems()) {
                List<String> row = new ArrayList<>(lineRow(order, line));
                row.add(date(order.getOpenedAt(), order.getCreatedAt()));
                row.add(time(order.getOpenedAt(), order.getCreatedAt()));
                row.add(date(order.getVoidedAt(), order.getVoidedAt()));
                row.add(time(order.getVoidedAt(), order.getVoidedAt()));
                row.add(text(order.getOperatorCode()));
                rows.add(row);
            }
        }
        List<String> columns = new ArrayList<>(lineColumns());
        columns.addAll(List.of(
                "Дата на откриване", "Час на откриване",
                "Дата на анулиране", "Час на анулиране",
                "Код на оператор"
        ));
        return table("18.5", "Анулирани продажби", columns, rows);
    }

    private Annex29TableResponse deliverySummary(LocalDate from, LocalDate to, String operatorCode) {
        List<List<String>> rows = new ArrayList<>();
        for (DeliveryEntity delivery : deliveries(from, to, operatorCode)) {
            double net = deliveryNet(delivery);
            rows.add(List.of(
                    text(delivery.getDeliveryId()),
                    delivery.getDeliveryDate() == null ? "" : delivery.getDeliveryDate().format(DATE),
                    time(delivery.getCreatedAt(), delivery.getCreatedAt()),
                    operatorCodeOf(delivery.getCreatedBy()),
                    "",
                    text(delivery.getSupplierName()),
                    text(delivery.getReferenceNumber()),
                    delivery.getDeliveryDate() == null ? "" : delivery.getDeliveryDate().format(DATE),
                    money(net),
                    money(0),
                    money(0),
                    money(net),
                    ""
            ));
        }
        return table("18.6", "Обобщени данни за доставки", List.of(
                "ID на запис", "Дата на доставка", "Час", "Код на оператор",
                "Доставчик код", "Доставчик име", "Фактура номер", "Фактура дата",
                "Сума без отстъпка без ДДС, евро", "Отстъпка, евро", "ДДС, евро", "Обща сума, евро",
                "Вид плащане"
        ), rows);
    }

    private Annex29TableResponse deliveryLines(LocalDate from, LocalDate to, String operatorCode) {
        List<List<String>> rows = new ArrayList<>();
        for (DeliveryEntity delivery : deliveries(from, to, operatorCode)) {
            if (delivery.getLines() == null) {
                continue;
            }
            for (DeliveryLineEntity line : delivery.getLines()) {
                double total = line.getLineTotal() == null
                        ? amount(line.getUnitCost()) * (line.getQuantity() == null ? 0 : line.getQuantity())
                        : line.getLineTotal().doubleValue();
                rows.add(List.of(
                        text(delivery.getDeliveryId()),
                        text(line.getItemId()),
                        text(line.getItemName()),
                        line.getQuantity() == null ? "" : String.valueOf(line.getQuantity()),
                        money(amount(line.getUnitCost())),
                        money(0),
                        money(0),
                        money(total)
                ));
            }
        }
        return table("18.7", "Детайлни данни за доставки", List.of(
                "ID на запис", "Код на стока", "Наименование", "Количество",
                "Единична цена, евро", "Отстъпка, евро", "ДДС, евро", "Обща сума, евро"
        ), rows);
    }

    private Annex29TableResponse stockMovement(LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.atTime(23, 59, 59);
        Map<String, List<StockMovementEntity>> byItem = stockMovementRepository.findAll().stream()
                .filter(row -> row.getItemId() != null)
                .collect(Collectors.groupingBy(StockMovementEntity::getItemId));
        List<List<String>> rows = new ArrayList<>();
        for (ItemEntity item : itemRepository.findAll()) {
            List<StockMovementEntity> movements = new ArrayList<>(byItem.getOrDefault(item.getItemId(), List.of()));
            movements.sort(Comparator.comparing(row -> row.getCreatedAt() == null ? LocalDateTime.MIN : row.getCreatedAt()));
            StockMovementEntity lastBefore = null;
            StockMovementEntity firstInPeriod = null;
            int debitQty = 0;
            int creditQty = 0;
            for (StockMovementEntity movement : movements) {
                LocalDateTime at = movement.getCreatedAt();
                if (at == null || at.isBefore(start)) {
                    lastBefore = movement;
                    continue;
                }
                if (at.isAfter(end)) {
                    continue;
                }
                if (firstInPeriod == null) {
                    firstInPeriod = movement;
                }
                int delta = quantity(movement.getNewQuantity()) - quantity(movement.getPreviousQuantity());
                if (delta >= 0) {
                    debitQty += delta;
                } else {
                    creditQty += -delta;
                }
            }
            int opening = lastBefore != null
                    ? quantity(lastBefore.getNewQuantity())
                    : (firstInPeriod != null ? quantity(firstInPeriod.getPreviousQuantity()) : quantity(item.getStockQuantity()));
            int closing = opening + debitQty - creditQty;
            double unit = item.getCostPrice() != null
                    ? item.getCostPrice().doubleValue()
                    : (item.getPrice() == null ? 0 : item.getPrice().doubleValue());
            rows.add(List.of(
                    text(item.getItemId()),
                    text(item.getName()),
                    String.valueOf(opening),
                    money(opening * unit),
                    String.valueOf(debitQty),
                    money(debitQty * unit),
                    String.valueOf(creditQty),
                    money(creditQty * unit),
                    String.valueOf(closing),
                    money(closing * unit)
            ));
        }
        return table("18.8", "Движение на стоки за период", List.of(
                "Код на стока", "Наименование",
                "Количество в началото", "Стойност в началото, евро",
                "Дебит количество", "Дебит стойност, евро",
                "Кредит количество", "Кредит стойност, евро",
                "Количество в края", "Стойност в края, евро"
        ), rows);
    }

    private Annex29TableResponse operators() {
        Map<String, List<UserRolePeriodEntity>> periods = userRolePeriodRepository.findAllByOrderByActiveFromAsc().stream()
                .collect(Collectors.groupingBy(UserRolePeriodEntity::getUserId, LinkedHashMap::new, Collectors.toList()));
        List<List<String>> rows = new ArrayList<>();
        for (UserEntity user : userRepository.findAll()) {
            String periodText = periods.getOrDefault(user.getUserId(), List.of()).stream()
                    .map(period -> period.getRole() + " " + period.getActiveFrom() + "–" + (period.getActiveTo() == null ? "активен" : period.getActiveTo()))
                    .collect(Collectors.joining("; "));
            if (periodText.isBlank() && user.getRoleActiveFrom() != null) {
                periodText = user.getRole() + " " + user.getRoleActiveFrom() + "–активен";
            }
            rows.add(List.of(
                    text(user.getOperatorCode()),
                    OperatorNames.display(user),
                    text(user.getPosition()),
                    stamp(user.getCreatedAt()),
                    stamp(user.getUpdatedAt()),
                    user.getRoleActiveTo() == null ? "" : user.getRoleActiveTo().format(DATE),
                    periodText
            ));
        }
        return table("18.9.operators", "Оператори", List.of(
                "Код", "Имена", "Длъжност", "Дата на конфигуриране", "Дата на последна промяна",
                "Дата на деактивиране", "Роли и период"
        ), rows);
    }

    private Annex29TableResponse roles() {
        return table("18.9.roles", "Роли", List.of(
                "Код", "Наименование", "Права", "Дата на конфигуриране", "Дата на деактивиране"
        ), List.of(
                List.of("ROLE_USER", "Касиер", "продажба, сторно, анулиране, каса", configuredAt.toString(), ""),
                List.of("ROLE_ADMIN", "Администратор", "номенклатури, потребители, справки, продажба", configuredAt.toString(), ""),
                List.of("ROLE_AUDITOR", "Одитор", "четене на дневник, справки и експорт", configuredAt.toString(), "")
        ));
    }

    private Annex29TableResponse rights() {
        return table("18.9.rights", "Права", List.of(
                "Код", "Наименование", "Описание", "Дата на конфигуриране", "Дата на деактивиране"
        ), List.of(
                List.of("SALE", "Продажба", "Откриване и приключване на продажба", configuredAt.toString(), ""),
                List.of("STORNO", "Сторно", "Сторниране на приключена продажба", configuredAt.toString(), ""),
                List.of("VOID", "Анулиране", "Отказ на продажба след неуспешно плащане", configuredAt.toString(), ""),
                List.of("NOMENCLATURE", "Номенклатури", "Промяна на стоки, цени и категории", configuredAt.toString(), ""),
                List.of("READ", "Четене", "Дневник, справки и експорт", configuredAt.toString(), "")
        ));
    }

    private Annex29TableResponse paymentTypes() {
        return table("18.9.payments", "Видове плащания", List.of(
                "Код", "Наименование", "Дата на конфигуриране", "Дата на последна промяна", "Дата на деактивиране"
        ), List.of(
                List.of("CASH", "В брой", configuredAt.toString(), configuredAt.toString(), ""),
                List.of("CARD", "С карта", configuredAt.toString(), configuredAt.toString(), ""),
                List.of("SPLIT", "Смесено", configuredAt.toString(), configuredAt.toString(), "")
        ));
    }

    private Annex29TableResponse actionTypes() {
        List<List<String>> rows = new ArrayList<>();
        for (AuditAction action : AuditAction.values()) {
            rows.add(List.of(action.name(), actionLabel(action), configuredAt.toString(), configuredAt.toString(), ""));
        }
        return table("18.9.actions", "Видове действия", List.of(
                "Код", "Наименование", "Дата на конфигуриране", "Дата на последна промяна", "Дата на деактивиране"
        ), rows);
    }

    private Annex29TableResponse sites() {
        return table("18.9.sites", "Търговски обекти", List.of(
                "Код", "Наименование", "Местонахождение", "Дата на конфигуриране", "Дата на последна промяна", "Дата на деактивиране"
        ), List.of(List.of(shopCode, shopName, "", configuredAt.toString(), configuredAt.toString(), "")));
    }

    private Annex29TableResponse workplaces() {
        List<List<String>> rows = fiscalDeviceRepository.findAll().stream()
                .map(device -> List.of(
                        text(device.getIndividualNumber() == null ? device.getSerialNumber() : device.getIndividualNumber()),
                        shopCode,
                        text(device.getIndividualNumber()),
                        configuredAt.toString(),
                        device.getRegistrationDate() == null ? "" : device.getRegistrationDate().format(DATE),
                        device.getStatus() == FiscalDeviceEntity.DeviceStatus.ACTIVE ? "" : DATE.format(LocalDate.now())
                ))
                .toList();
        return table("18.9.workplaces", "Работни места", List.of(
                "Код", "Търговски обект", "Индивидуален номер на ФУ",
                "Дата на конфигуриране", "Дата на последна промяна", "Дата на деактивиране"
        ), rows);
    }

    private Annex29TableResponse customers() {
        List<List<String>> rows = customerRepository.findAll().stream()
                .map(customer -> List.of(
                        text(customer.getCustomerId()),
                        OperatorNames.display(customer.getFirstName(), null, customer.getLastName(), customer.getEmail()),
                        stamp(customer.getCreatedAt()),
                        stamp(customer.getUpdatedAt()),
                        ""
                ))
                .toList();
        return table("18.9.customers", "Клиенти", List.of(
                "Идентификатор", "Имена", "Дата на конфигуриране", "Дата на последна промяна", "Дата на деактивиране"
        ), rows);
    }

    private Annex29TableResponse suppliers() {
        Map<String, String> suppliers = new LinkedHashMap<>();
        for (DeliveryEntity delivery : deliveryRepository.findAll()) {
            if (delivery.getSupplierName() != null && !delivery.getSupplierName().isBlank()) {
                suppliers.putIfAbsent(delivery.getSupplierName().trim(), "");
            }
        }
        for (ItemEntity item : itemRepository.findAll()) {
            if (item.getSupplierName() != null && !item.getSupplierName().isBlank()) {
                suppliers.putIfAbsent(item.getSupplierName().trim(), item.getSupplierCode() == null ? "" : item.getSupplierCode());
            }
        }
        List<List<String>> rows = suppliers.entrySet().stream()
                .map(entry -> List.of(entry.getValue(), entry.getKey(), configuredAt.toString(), configuredAt.toString(), ""))
                .toList();
        return table("18.9.suppliers", "Доставчици", List.of(
                "Идентификатор", "Имена", "Дата на конфигуриране", "Дата на последна промяна", "Дата на деактивиране"
        ), rows);
    }

    private List<String> lineColumns() {
        return List.of(
                "УНП", "Системен номер", "Код на стока", "Наименование", "Количество",
                "Единична цена без ДДС, евро", "Отстъпка, евро", "ДДС ставка", "ДДС, евро", "Обща сума, евро"
        );
    }

    private List<String> lineRow(OrderEntity order, OrderItemEntity line) {
        double qty = line.getQuantity() == null ? 0 : line.getQuantity();
        double rate = line.getVatRate() == null ? 0.20 : line.getVatRate();
        double gross = (line.getPrice() == null ? 0 : line.getPrice()) * qty;
        double net = rate > 0 ? gross / (1.0 + rate) : gross;
        double unitNet = qty == 0 ? 0 : net / qty;
        return List.of(
                text(order.getUniqueSaleNumber()),
                text(order.getOrderId()),
                text(line.getItemId()),
                text(line.getName()),
                String.format(Locale.US, "%.3f", qty),
                money(unitNet),
                money(0),
                String.format(Locale.US, "%.2f", rate * 100),
                money(gross - net),
                money(gross)
        );
    }

    private List<OrderEntity> originals(List<OrderEntity> orders) {
        return orders.stream().filter(order -> order.getOriginalOrderId() == null).toList();
    }

    private List<DeliveryEntity> deliveries(LocalDate from, LocalDate to, String operatorCode) {
        String code = operatorCode == null ? "" : operatorCode.trim();
        return deliveryRepository.findAll().stream()
                .filter(delivery -> delivery.getDeliveryDate() != null
                        && !delivery.getDeliveryDate().isBefore(from)
                        && !delivery.getDeliveryDate().isAfter(to))
                .filter(delivery -> code.isEmpty() || code.equals(operatorCodeOf(delivery.getCreatedBy())))
                .sorted(Comparator.comparing(DeliveryEntity::getDeliveryDate))
                .toList();
    }

    private boolean matches(OrderEntity order, String device, String operatorCode) {
        if (operatorCode != null && !operatorCode.isBlank() && !operatorCode.equals(order.getOperatorCode())) {
            return false;
        }
        return device == null || device.isBlank() || device.equals(order.getFiscalIndividualNumber());
    }

    private String operatorCodeOf(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        return userRepository.findByEmail(email).map(UserEntity::getOperatorCode).orElse("");
    }

    private double deliveryNet(DeliveryEntity delivery) {
        if (delivery.getLines() == null) {
            return 0;
        }
        return delivery.getLines().stream()
                .mapToDouble(line -> line.getLineTotal() == null ? 0 : line.getLineTotal().doubleValue())
                .sum();
    }

    private Annex29TableResponse table(String code, String title, List<String> columns, List<List<String>> rows) {
        return Annex29TableResponse.builder().code(code).title(title).columns(columns).rows(rows).build();
    }

    private String actionLabel(AuditAction action) {
        return switch (action) {
            case LOGIN -> "Вход";
            case LOGOUT -> "Изход";
            case USER_CREATE -> "Въвеждане на оператор";
            case USER_CHANGE -> "Промяна на оператор";
            case STORNO -> "Сторниране";
            case VOID -> "Анулиране";
            case NOMENCLATURE_CHANGE -> "Промяна на номенклатура";
        };
    }

    private String date(LocalDateTime primary, LocalDateTime fallback) {
        LocalDateTime value = primary != null ? primary : fallback;
        return value == null ? "" : value.format(DATE);
    }

    private String time(LocalDateTime primary, LocalDateTime fallback) {
        LocalDateTime value = primary != null ? primary : fallback;
        return value == null ? "" : value.format(TIME);
    }

    private String stamp(java.sql.Timestamp value) {
        return value == null ? "" : value.toLocalDateTime().format(DATE);
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String money(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private double amount(Double value) {
        return value == null ? 0 : value;
    }

    private double amount(java.math.BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }

    private int quantity(Integer value) {
        return value == null ? 0 : value;
    }

    private String cell(String value) {
        String safe = value == null ? "" : value.replace("\"", "\"\"");
        if (safe.contains(";") || safe.contains("\"") || safe.contains("\n")) {
            return "\"" + safe + "\"";
        }
        return safe;
    }
}
