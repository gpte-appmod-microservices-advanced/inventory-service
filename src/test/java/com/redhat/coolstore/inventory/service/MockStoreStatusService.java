package com.redhat.coolstore.inventory.service;

import javax.enterprise.inject.Specializes;

@Specializes
public class MockStoreStatusService extends StoreStatusService {

    @Override
    public String storeStatus(String store) {
        return "MOCK";
    }
}
