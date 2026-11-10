package com.bts.personalbudget.core.domain.service.cardpurchase;

import com.bts.personalbudget.core.domain.entity.CreditCardEntity;
import com.bts.personalbudget.core.domain.service.creditcard.CardInvoiceCalculator;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.repository.CardPurchaseRepository;
import com.bts.personalbudget.repository.CreditCardRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates a card statement before persisting it and keeps re-imports idempotent. */
@Slf4j
@RequiredArgsConstructor
@Service
public class CardPurchaseBatchService {
    private static final int MAX_ITEMS = 300;
    private final CreditCardRepository cardRepository;
    private final CardPurchaseRepository purchaseRepository;
    private final CardPurchaseService purchaseService;

    @Transactional
    public List<CardPurchaseBatchLine> process(final UUID cardCode, final boolean dryRun,
                                               final List<CardPurchaseBatchItem> items) {
        log.info("m=process, cardCode={}, dryRun={}, items={}", cardCode, dryRun, items == null ? null : items.size());
        if (items == null || items.isEmpty() || items.size() > MAX_ITEMS) {
            throw new InvalidFieldsException("Campos inválidos", CardPurchaseBatchService.class,
                    Map.of("items", "deve conter entre 1 e " + MAX_ITEMS + " linhas"));
        }
        final CreditCardEntity card = cardRepository.findByCode(cardCode).orElseThrow(() ->
                new EntityNotFoundException("CreditCardEntity not found for code " + cardCode));
        final Map<String, Integer> ordinals = new HashMap<>();
        final List<CardPurchaseBatchLine> preview = new ArrayList<>();
        for (final CardPurchaseBatchItem item : items) {
            final String basic = basicKey(item);
            final int ordinal = ordinals.merge(basic, 1, Integer::sum);
            preview.add(preview(card, item, importKey(card.getId(), basic, ordinal)));
        }
        if (dryRun) return preview;

        final Map<String, String> validations = new HashMap<>();
        for (int i = 0; i < preview.size(); i++) {
            final CardPurchaseBatchLine line = preview.get(i);
            if (line.status() == CardPurchaseBatchStatus.ERROR) {
                for (final Map.Entry<String, String> error : line.errors().entrySet()) {
                    validations.put("items[" + i + "]." + error.getKey(), error.getValue());
                }
            }
            if (line.status() == CardPurchaseBatchStatus.POSSIBLE_DUPLICATE
                    && !Boolean.TRUE.equals(items.get(i).acceptDuplicate())) {
                throw new ConflictException("Há possíveis duplicidades; confirme as linhas antes de importar");
            }
        }
        if (!validations.isEmpty()) throw new InvalidFieldsException("Campos inválidos", CardPurchaseBatchService.class, validations);

        final List<CardPurchaseBatchLine> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            final CardPurchaseBatchLine line = preview.get(i);
            if (line.status() == CardPurchaseBatchStatus.DUPLICATE) {
                result.add(line);
                continue;
            }
            final CardPurchase created = purchaseService.create(toPurchase(cardCode, items.get(i), line.importKey()));
            final CardInstallment first = created.installmentList().isEmpty() ? null : created.installmentList().get(0);
            result.add(new CardPurchaseBatchLine(CardPurchaseBatchStatus.NEW, Map.of(), line.importKey(),
                    first == null ? null : first.invoiceReferenceMonth(), first == null ? null : first.dueDate(),
                    created.cardHolderCode(), created.thirdPartyCode(), List.of()));
        }
        return result;
    }

    private CardPurchaseBatchLine preview(final CreditCardEntity card, final CardPurchaseBatchItem item,
                                          final String importKey) {
        final Map<String, String> errors = basicErrors(item);
        if (!errors.isEmpty()) return new CardPurchaseBatchLine(CardPurchaseBatchStatus.ERROR, errors, importKey,
                null, null, null, null, List.of());
        if (purchaseRepository.findByCardIdAndImportKey(card.getId(), importKey).isPresent()) {
            return new CardPurchaseBatchLine(CardPurchaseBatchStatus.DUPLICATE, Map.of(), importKey,
                    null, null, null, null, List.of());
        }
        final int first = item.firstInstallment() == null ? 1 : item.firstInstallment();
        final CardInvoiceCalculator.InvoiceSlot slot = CardInvoiceCalculator.installmentSlots(item.purchaseDate(),
                item.installments(), card.getClosingDay(), card.getDueDay()).get(first - 1);
        final List<UUID> matches = purchaseRepository.findPossibleDuplicates(card.getId(), item.description().trim(),
                item.installmentAmount(), item.installments()).stream().map(purchase -> purchase.getCode()).toList();
        final CardPurchaseBatchStatus status = matches.isEmpty() ? CardPurchaseBatchStatus.NEW
                : CardPurchaseBatchStatus.POSSIBLE_DUPLICATE;
        return new CardPurchaseBatchLine(status, Map.of(), importKey, slot.referenceMonth(), slot.dueDate(),
                item.cardHolderCode(), item.thirdPartyCode(), matches);
    }

    private static CardPurchase toPurchase(final UUID cardCode, final CardPurchaseBatchItem item,
                                           final String importKey) {
        return new CardPurchase(null, cardCode, item.description(), item.purchaseDate(), item.installments(),
                item.installmentAmount(), null, item.categoryCode(), List.of(), item.cardHolderCode(), null,
                item.cardLastDigits(), item.thirdPartyCode(), null, item.ownExpense(),
                item.firstInstallment() == null ? 1 : item.firstInstallment(), importKey);
    }

    private static Map<String, String> basicErrors(final CardPurchaseBatchItem item) {
        final Map<String, String> errors = new HashMap<>();
        if (item.description() == null || item.description().trim().isEmpty() || item.description().trim().length() > 100) {
            errors.put("description", "deve ter entre 1 e 100 caracteres");
        }
        if (item.purchaseDate() == null) errors.put("purchaseDate", "deve ser informada");
        if (item.installments() == null || item.installments() < 1 || item.installments() > 60) {
            errors.put("installments", "deve estar entre 1 e 60");
        }
        if (item.firstInstallment() != null && (item.firstInstallment() < 1 || item.installments() == null
                || item.firstInstallment() > item.installments())) errors.put("firstInstallment", "deve estar entre 1 e installments");
        if (item.installmentAmount() == null || item.installmentAmount().signum() <= 0
                || item.installmentAmount().stripTrailingZeros().scale() > 2) {
            errors.put("installmentAmount", "deve ser positivo com no máximo 2 casas decimais");
        }
        return errors;
    }

    private static String basicKey(final CardPurchaseBatchItem item) {
        return String.valueOf(item.purchaseDate()) + "|" + normalize(item.description()) + "|"
                + item.installmentAmount() + "|" + item.installments();
    }

    private static String importKey(final Long cardId, final String basicKey, final int ordinal) {
        try {
            final byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((cardId + "|" + basicKey + "|" + ordinal).getBytes(StandardCharsets.UTF_8));
            final StringBuilder result = new StringBuilder(64);
            for (byte b : hash) result.append(String.format("%02x", b));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }

    private static String normalize(final String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
