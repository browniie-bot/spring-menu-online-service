package com.menuonline.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.menuonline.exceptions.HttpServiceException;
import com.menuonline.payloads.AvailablePlansResponse;
import com.menuonline.payloads.SubscriptionDetailResponse;
import com.menuonline.payloads.AvailablePlansResponse.AvailablePriceResponse;
import com.menuonline.payloads.AvailablePlansResponse.PlanRecurringInterval;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Price;
import com.stripe.model.Product;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerListParams;
import com.stripe.param.PriceListParams;
import com.stripe.param.PriceRetrieveParams;
import com.stripe.param.ProductListParams;
import com.stripe.param.SubscriptionCancelParams;
import com.stripe.param.SubscriptionListParams;
import com.stripe.param.SubscriptionRetrieveParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.service.V1Services;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class StripeService {

    private final StripeClient client;

    public StripeService(@Value("${stripe.secretKey}") String secretKey) {
        client = new StripeClient(secretKey);
    }

    public Optional<Customer> findCustomerByEmail(String email) {
        CustomerListParams params = CustomerListParams.builder()
                .setEmail(email)
                .build();
        V1Services v1 = client.v1();
        try {
            List<Customer> data = v1.customers().list(params).getData();
            log.info("findSubscriptionByEmail - email:{} number of customers:{}", email, data.size());
            return data.stream().max(Comparator.comparingLong(c -> c.getCreated()));
        } catch (StripeException e) {
            log.warn("findCustomerByEmail - email:{} exception:{}", email, e.getLocalizedMessage());
            return Optional.empty();
        }
    }

    public Optional<String> findEmailByCustomer(String customer) {
        try {
            Customer customerObject = client.v1().customers().retrieve(customer);
            if (customerObject == null)
                return Optional.empty();

            return Optional.of(customerObject.getEmail());
        } catch (StripeException e) {
            log.warn("findEmailByCustomer - customer:{} exception:{}", customer, e.getLocalizedMessage());
            return Optional.empty();
        }
    }

    /**
     * @param email
     * @return all subscriptions but canceled
     */
    public Optional<?> findSubscriptionByEmail(String email) {
        try {
            CustomerListParams params = CustomerListParams.builder()
                    .setEmail(email)
                    .build();
            V1Services v1 = client.v1();
            List<Customer> data = v1.customers().list(params).getData();
            log.info("findSubscriptionByEmail - email:{} number of customers:{}", email, data.size());
            Optional<Customer> customer = data.stream().max(Comparator.comparingLong(c -> c.getCreated()));
            return customer.flatMap(c -> {
                SubscriptionListParams subsParam = SubscriptionListParams.builder()
                        .setCustomer(c.getId())
                        .addExpand("data.default_payment_method")
                        .addExpand("data.latest_invoice")
                        .build();
                try {
                    List<Subscription> subscriptions = v1.subscriptions().list(subsParam).getData();
                    subscriptions.forEach(s -> s.getDaysUntilDue());
                    return subscriptions.stream()
                            .max(Comparator.comparingLong(s -> s.getCreated()));
                } catch (StripeException e) {
                    e.printStackTrace();
                    log.warn("findSubscriptionByEmail - exception: {}", e.getMessage());
                    return Optional.empty();
                }
            });
        } catch (StripeException e) {
            log.warn("findSubscriptionByEmail - email:{} exception: {}", email, e.getLocalizedMessage());
        }
        return Optional.empty();
    }

    public Optional<Subscription> findSubscriptionByCustomerId(String customerId) {
        try {
            Customer customer = client.v1().customers().retrieve(customerId);

            if (customer.getSubscriptions() == null) {
                log.info("findSubscriptionByCustomerId - no subs find for customer:{}", customerId);
                return Optional.empty();
            }

            List<Subscription> data = customer.getSubscriptions().getData();
            log.info("findSubscriptionByCustomerId - customer:{} subscription size:{}",
                    customerId, data.size());
            return data.stream()
                    .max(Comparator.comparingLong(s -> s.getCreated()));
        } catch (StripeException e) {
            log.warn("findSubscriptionByCustomerId - exception: {}", e.getMessage());
            throw new HttpServiceException(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public Optional<Subscription> findSubscriptionById(String subsId) {
        try {
            SubscriptionRetrieveParams param = SubscriptionRetrieveParams.builder()
                    .addExpand("customer")
                    .build();
            var subs = client.v1().subscriptions().retrieve(subsId, param);
            return Optional.ofNullable(subs);
        } catch (StripeException e) {
            e.printStackTrace();
            log.warn("findSubscriptionById - exception: {}", e.getMessage());
            throw new HttpServiceException(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public Optional<String> generateChangePaymentMethodUrl(String customer, String subscription) {
        log.info("generateChangePaymentMethodUrl - customer:{} subscription:{}", customer, subscription);
        try {
            V1Services v1 = client.v1();

            SessionCreateParams sessionCreateParams = SessionCreateParams.builder()
                    .setCustomer(customer)
                    .setLocale(SessionCreateParams.Locale.PT_BR)
                    .addAllowedPaymentMethodType(SessionCreateParams.AllowedPaymentMethodType.CARD)
                    .setSetupIntentData(SessionCreateParams.SetupIntentData.builder()
                            .putMetadata("customer_id", customer)
                            .putMetadata("subscription_id", subscription)
                            .build())
                    .setMode(SessionCreateParams.Mode.SETUP)
                    .setSuccessUrl("https://itimenu.app")
                    .build();
            Session session;
            session = v1.checkout().sessions().create(sessionCreateParams);
            return Optional.of(session.getUrl());
        } catch (StripeException e) {
            log.warn("generateChangePaymentMethodUrl - customer:{} subscription:{} exception: {}",
                    customer, subscription, e);
            return Optional.empty();
        }
    }

    public String cancel(String subscriptionId) {
        SubscriptionCancelParams params = SubscriptionCancelParams.builder()
                .setProrate(true)
                .build();
        try {
            Subscription cancel = client.v1().subscriptions().cancel(subscriptionId, params);
            return cancel.getId();
        } catch (StripeException e) {
            log.warn("cancel - exception: {}", e.getMessage());
            throw new HttpServiceException(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public Optional<String> generateNewPlanUrl(String email, String priceId) {
        log.info("generateNewPlanUrl - email:{}", email);
        try {
            SessionCreateParams sessionCreateParams = SessionCreateParams.builder()
                    .setSuccessUrl("https://itimenu.app/post-sale")
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setSubmitType(SessionCreateParams.SubmitType.SUBSCRIBE)
                    .setCustomerEmail(email)
                    .setLocale(SessionCreateParams.Locale.PT_BR)
                    .setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
                            .setDescription(getDescription(priceId))
                            .build())
                    .setBrandingSettings(SessionCreateParams.BrandingSettings
                            .builder()
                            .setFontFamily(SessionCreateParams.BrandingSettings.FontFamily.ROBOTO)
                            .build())
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1l)
                            .setPrice(priceId)
                            .build())
                    .build();
            Session session = client.v1().checkout().sessions().create(sessionCreateParams);
            return Optional.ofNullable(session).map(Session::getUrl);
        } catch (StripeException e) {
            log.warn("generateNewPlanUrl - exception: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String getDescription(String priceId) throws StripeException {
        Price price = client.v1().prices().retrieve(priceId,
                PriceRetrieveParams.builder().addExpand("product").build());
        return price.getProductObject().getDescription();
    }

    public Optional<SubscriptionDetailResponse> findDetails(String subscriptionId) {
        try {
            SubscriptionRetrieveParams params = SubscriptionRetrieveParams.builder()
                    .addExpand("default_payment_method").build();
            Subscription subscription = client.v1().subscriptions().retrieve(subscriptionId, params);
            return Optional.ofNullable(SubscriptionDetailResponse.from(subscription));
        } catch (StripeException e) {
            log.warn("findDetails - exception: {}", e.getMessage());
        }
        return Optional.empty();
    }


    public List<AvailablePlansResponse> findPlans() {
        try {
            List<AvailablePlansResponse> plansResponse = new ArrayList<>();
            List<Price> prices = client.v1().prices()
                    .list(PriceListParams.builder().setActive(true).build()).getData();
            List<Product> data = client.v1().products()
                    .list(ProductListParams.builder().setActive(true).build()).getData();

            for (Product product : data) {
                String metadataDescription = product.getMetadata().get("description");
                List<String> description = metadataDescription != null
                        ? List.of(metadataDescription.split("<break>"))
                        : List.of();
                List<AvailablePriceResponse> pricesResponse = prices.stream()
                        .filter(price -> price.getProduct().equals(product.getId()))
                        .map(price -> {
                            String priceSavingValue = price.getMetadata().get("saving");
                            BigDecimal savingValue = null;
                            if (priceSavingValue != null) {
                                savingValue = BigDecimal
                                        .valueOf(Integer.valueOf(priceSavingValue))
                                        .divide(BigDecimal.valueOf(100));
                            }
                            BigDecimal finalValue = BigDecimal
                                    .valueOf(price.getUnitAmount())
                                    .divide(BigDecimal.valueOf(100));
                            PlanRecurringInterval planRecurringInterval = PlanRecurringInterval
                                    .get(price.getRecurring().getInterval());
                            return new AvailablePriceResponse(price.getId(), finalValue,
                                    planRecurringInterval, savingValue);
                        }).toList();
                plansResponse.add(new AvailablePlansResponse(
                        product.getId(), product.getName(), description, pricesResponse));

            }

            return plansResponse;
        } catch (Exception e) {
            log.error("findPlans ", e);
        }
        return List.of();
    }

}
