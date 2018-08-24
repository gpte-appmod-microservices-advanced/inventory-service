package com.redhat.coolstore.inventory.rest;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.redhat.coolstore.inventory.model.Inventory;
import com.redhat.coolstore.inventory.service.InventoryService;
import com.redhat.coolstore.inventory.service.StoreStatusService;

@Path("/inventory")
@RequestScoped
public class InventoryResource {

    @Inject
    private InventoryService inventoryService;

    @Inject
    private StoreStatusService storeStatusService;

    @GET
    @Path("/{itemId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Inventory getInventory(@PathParam("itemId") String itemId, @DefaultValue("false") @QueryParam("storeStatus") boolean storeStatus) {
        Inventory inventory = inventoryService.getInventory(itemId);
        if (inventory == null) {
            throw new NotFoundException();
        } else {
            if (storeStatus) {
                String status = storeStatusService.storeStatus(inventory.getLocation());
                inventory.setLocation(inventory.getLocation() + " [" + status + "]");
            }
            return inventory;
        }
    }
}

