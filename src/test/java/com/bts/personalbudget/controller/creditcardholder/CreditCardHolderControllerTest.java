package com.bts.personalbudget.controller.creditcardholder;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bts.personalbudget.core.domain.enumerator.CreditCardHolderKind;
import com.bts.personalbudget.core.domain.enumerator.CreditCardHolderStatus;
import com.bts.personalbudget.core.domain.enumerator.CreditCardPlasticKind;
import com.bts.personalbudget.core.domain.enumerator.CreditCardPlasticStatus;
import com.bts.personalbudget.core.domain.service.creditcardholder.CreditCardHolder;
import com.bts.personalbudget.core.domain.service.creditcardholder.CreditCardHolderService;
import com.bts.personalbudget.core.domain.service.creditcardholder.CreditCardPlastic;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.ControllerExceptionHandler;
import com.bts.personalbudget.mapper.CreditCardHolderMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CreditCardHolderControllerTest {

    private static final UUID CARD = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID HOLDER = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000a1");
    private static final UUID PLASTIC = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000b1");
    private static final UUID THIRD_PARTY = UUID.fromString("cccccccc-0000-0000-0000-0000000000c1");

    @Mock private CreditCardHolderService service;
    @Spy private CreditCardHolderMapper mapper = new CreditCardHolderMapper();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CreditCardHolderController(service, mapper),
                        new CreditCardPlasticController(service, mapper))
                .setControllerAdvice(new ControllerExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()
                        .findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .build();
    }

    @Test
    void shouldCreateAnAdditionalHolderWithCreatedStatusAndSnakeCaseBody() throws Exception {
        when(service.createAdditional(CARD, "Márcia")).thenReturn(holder("Márcia", CreditCardHolderKind.ADICIONAL));

        mvc.perform(post("/credit_card/{cardCode}/holder", CARD).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Márcia\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(HOLDER.toString()))
                .andExpect(jsonPath("$.card_code").value(CARD.toString()))
                .andExpect(jsonPath("$.kind").value("ADICIONAL"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldRejectABlankHolderNameBeforeCallingTheService() throws Exception {
        mvc.perform(post("/credit_card/{cardCode}/holder", CARD).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void shouldListTheHoldersOfACard() throws Exception {
        when(service.findHolders(CARD)).thenReturn(List.of(
                holder("Titular", CreditCardHolderKind.TITULAR), holder("Márcia", CreditCardHolderKind.ADICIONAL)));

        mvc.perform(get("/credit_card/{cardCode}/holder", CARD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].kind").value("TITULAR"));
    }

    @Test
    void shouldAnswer409WhenTheTitularCannotBeInactivated() throws Exception {
        when(service.changeHolderStatus(CARD, HOLDER, CreditCardHolderStatus.INACTIVE))
                .thenThrow(new ConflictException("TITULAR não pode ser inativado"));

        mvc.perform(patch("/credit_card/{cardCode}/holder/{holderCode}/inactivate", CARD, HOLDER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("TITULAR não pode ser inativado"));
    }

    @Test
    void shouldRegisterAPlasticWithoutAKindAndGetItBackWithItsHolder() throws Exception {
        when(service.createPlastic(eq(CARD), eq(HOLDER), eq("3202"), isNull()))
                .thenReturn(plastic(CreditCardPlasticKind.UNKNOWN, null));

        mvc.perform(post("/credit_card/{cardCode}/plastic", CARD).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holder_code\":\"" + HOLDER + "\",\"last_digits\":\"3202\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.last_digits").value("3202"))
                .andExpect(jsonPath("$.holder_code").value(HOLDER.toString()))
                .andExpect(jsonPath("$.holder_name").value("Márcia"))
                .andExpect(jsonPath("$.kind").value("UNKNOWN"));
    }

    @Test
    void shouldRejectLastDigitsThatAreNotFourDigitsBeforeCallingTheService() throws Exception {
        mvc.perform(post("/credit_card/{cardCode}/plastic", CARD).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holder_code\":\"" + HOLDER + "\",\"last_digits\":\"32\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void shouldPassThePlasticPatchFieldsAndTheClearFlagToTheService() throws Exception {
        when(service.updatePlastic(CARD, PLASTIC, null, CreditCardPlasticStatus.REPLACED, THIRD_PARTY, false))
                .thenReturn(plastic(CreditCardPlasticKind.VIRTUAL, THIRD_PARTY));

        mvc.perform(patch("/credit_card/{cardCode}/plastic/{plasticCode}", CARD, PLASTIC)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REPLACED\",\"default_third_party_code\":\"" + THIRD_PARTY + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.default_third_party_code").value(THIRD_PARTY.toString()));
    }

    private static CreditCardHolder holder(final String name, final CreditCardHolderKind kind) {
        return new CreditCardHolder(HOLDER, CARD, name, kind, CreditCardHolderStatus.ACTIVE);
    }

    private static CreditCardPlastic plastic(final CreditCardPlasticKind kind, final UUID defaultThirdParty) {
        return new CreditCardPlastic(PLASTIC, CARD, HOLDER, "Márcia", "3202", kind,
                CreditCardPlasticStatus.ACTIVE, defaultThirdParty);
    }
}
