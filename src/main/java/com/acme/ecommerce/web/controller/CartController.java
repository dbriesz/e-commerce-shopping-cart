package com.acme.ecommerce.web.controller;

import com.acme.ecommerce.domain.Product;
import com.acme.ecommerce.domain.ProductPurchase;
import com.acme.ecommerce.domain.Purchase;
import com.acme.ecommerce.domain.ShoppingCart;
import com.acme.ecommerce.web.FlashMessage;
import com.acme.ecommerce.web.exceptions.QuantityExceedsStockException;
import com.acme.ecommerce.service.ProductService;
import com.acme.ecommerce.service.PurchaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.math.BigDecimal;

import static com.acme.ecommerce.web.FlashMessage.Status.FAILURE;
import static com.acme.ecommerce.web.FlashMessage.Status.SUCCESS;

@Controller
@RequestMapping("/cart")
@Scope("request")
public class CartController {
	final Logger logger = LoggerFactory.getLogger(CartController.class);
	
	@Autowired
	PurchaseService purchaseService;
	
	@Autowired
	private ProductService productService;
	
	@Autowired
	private ShoppingCart sCart;
	
	@Autowired
	private HttpSession session;
	
    @RequestMapping("")
    public String viewCart(Model model) {
    	logger.debug("Getting Product List");
    	logger.debug("Session ID = " + session.getId());

        model.addAttribute("page_title", "View Cart");
    	Purchase purchase = sCart.getPurchase();
    	BigDecimal subTotal = new BigDecimal(0);
    	
    	model.addAttribute("purchase", purchase);
    	if (purchase != null) {
    		for (ProductPurchase pp : purchase.getProductPurchases()) {
    			logger.debug("cart has " + pp.getQuantity() + " of " + pp.getProduct().getName());
    			subTotal = subTotal.add(pp.getProduct().getPrice().multiply(new BigDecimal(pp.getQuantity())));
    		}
    		
    		model.addAttribute("subTotal", subTotal);
    	} else {
    		logger.error("No purchases Found for session ID=" + session.getId());
    		return "redirect:/error";
    	}
        CartController.addCartToModel(model, sCart);

        return "cart";
    }
    
    @RequestMapping(path="/add", method = RequestMethod.POST)
    public RedirectView addToCart(@ModelAttribute(value="productId") long productId,
                                  @ModelAttribute(value="quantity") int quantity,
                                  RedirectAttributes redirectAttributes) throws Exception {
    	boolean productAlreadyInCart = false;
    	RedirectView redirect = new RedirectView("/product/");
		redirect.setExposeModelAttributes(false);
    	
    	Product addProduct = productService.findById(productId);

        if (addProduct != null) {
            logger.debug("Adding Product: " + addProduct.getId());

            Purchase purchase = sCart.getPurchase();
            if (purchase == null) {
                purchase = new Purchase();
                sCart.setPurchase(purchase);
            } else {
                for (ProductPurchase pp : purchase.getProductPurchases()) {
                    if (pp.getProduct() != null) {
                        if (pp.getProduct().getId().equals(productId)) {
                            productService.checkIfProductInStock(addProduct, (pp.getQuantity() + quantity));
                            pp.setQuantity(pp.getQuantity() + quantity);
                            productAlreadyInCart = true;
                            break;
                        }
                    }
                }
            }
            if (!productAlreadyInCart) {
                productService.checkIfProductInStock(addProduct, quantity);
                ProductPurchase newProductPurchase = new ProductPurchase();
                newProductPurchase.setProduct(addProduct);
                newProductPurchase.setQuantity(quantity);
                newProductPurchase.setPurchase(purchase);
                purchase.getProductPurchases().add(newProductPurchase);
            }
            logger.debug("Added " + quantity + " of " + addProduct.getName() + " to cart");
            sCart.setPurchase(purchaseService.save(purchase));
            redirectAttributes.addFlashAttribute("flash",new FlashMessage("Added " + quantity + " " + addProduct.getName() + " to cart", SUCCESS));
        } else {
            logger.error("Attempt to add unknown product: " + productId);
            redirect.setUrl("/error");
        }

    	return redirect;
    }
 
    @RequestMapping(path="/update", method = RequestMethod.POST)
    public RedirectView updateCart(@ModelAttribute(value="productId") long productId,
                                   @ModelAttribute(value="newQuantity") int newQuantity,
                                   RedirectAttributes redirectAttributes) throws Exception {
    	logger.debug("Updating Product: " + productId + " with Quantity: " + newQuantity);
		RedirectView redirect = new RedirectView("/cart");
		redirect.setExposeModelAttributes(false);
    	
    	Product updateProduct = productService.findById(productId);
        productService.checkIfProductInStock(updateProduct, newQuantity);

        if (updateProduct != null) {
            Purchase purchase = sCart.getPurchase();
            if (purchase == null) {
                logger.error("Unable to find shopping cart for update");
                redirect.setUrl("/error");
            } else {
                for (ProductPurchase pp : purchase.getProductPurchases()) {
                    if (pp.getProduct() != null) {
                        if (pp.getProduct().getId().equals(productId)) {
                            if (newQuantity > 0) {
                                pp.setQuantity(newQuantity);
                                logger.debug("Updated " + updateProduct.getName() + " to " + newQuantity);
                            } else {
                                purchase.getProductPurchases().remove(pp);
                                logger.debug("Removed " + updateProduct.getName() + " because quantity was set to " + newQuantity);
                            }
                            break;
                        }
                    }
                }
            }
            sCart.setPurchase(purchaseService.save(purchase));
            redirectAttributes.addFlashAttribute("flash",new FlashMessage("Updated " + updateProduct.getName() + " quantity to " + newQuantity, SUCCESS));
        } else {
            logger.error("Attempt to update on non-existent product");
            redirect.setUrl("/error");
        }
    	
    	return redirect;
    }
    
    @RequestMapping(path="/remove", method = RequestMethod.POST)
    public RedirectView removeFromCart(@ModelAttribute(value="productId") long productId,
                                       RedirectAttributes redirectAttributes) {
    	logger.debug("Removing Product: " + productId);
		RedirectView redirect = new RedirectView("/cart");
		redirect.setExposeModelAttributes(false);
    	
    	Product updateProduct = productService.findById(productId);
    	if (updateProduct != null) {
    		Purchase purchase = sCart.getPurchase();
    		if (purchase != null) {
    			for (ProductPurchase pp : purchase.getProductPurchases()) {
    				if (pp.getProduct() != null) {
    					if (pp.getProduct().getId().equals(productId)) {
    						purchase.getProductPurchases().remove(pp);
   							logger.debug("Removed " + updateProduct.getName());
    						break;
    					}
    				}
    			}
    			purchase = purchaseService.save(purchase);
                redirectAttributes.addFlashAttribute("flash",new FlashMessage("Removed " + updateProduct.getName() + " from cart", SUCCESS));
    			sCart.setPurchase(purchase);
    			if (purchase.getProductPurchases().isEmpty()) {
        	    	//if last item in cart redirect to product else return cart
        			redirect.setUrl("/product/");
        		}
    		} else {
    			logger.error("Unable to find shopping cart for update");
    			redirect.setUrl("/error");
    		}
    	} else {
    		logger.error("Attempt to update on non-existent product");
    		redirect.setUrl("/error");
    	}

    	return redirect;
    }
    
    @RequestMapping(path="/empty", method = RequestMethod.POST)
    public RedirectView emptyCart(RedirectAttributes redirectAttributes) {
    	RedirectView redirect = new RedirectView("/product/");
		redirect.setExposeModelAttributes(false);
    	
    	logger.debug("Emptying Cart");
    	Purchase purchase = sCart.getPurchase();
		if (purchase != null) {
			purchase.getProductPurchases().clear();
			sCart.setPurchase(purchaseService.save(purchase));
            redirectAttributes.addFlashAttribute("flash",new FlashMessage("Cart successfully emptied!", SUCCESS));
		} else {
			logger.error("Unable to find shopping cart for update");
			redirect.setUrl("/error");
		}
		
    	return redirect;
    }

    @ExceptionHandler(QuantityExceedsStockException.class)
    public String exceedsStock(HttpServletRequest request, Exception ex) {
        FlashMap flashMap = RequestContextUtils.getOutputFlashMap(request);
        flashMap.put("flash", new FlashMessage(ex.getMessage(), FAILURE));
        return "redirect:" + request.getHeader("referer");
    }

    private static BigDecimal computeSubtotal(Purchase purchase) {
        BigDecimal subTotal = new BigDecimal(0);

        for (ProductPurchase pp : purchase.getProductPurchases()) {
            subTotal = subTotal.add(pp.getProduct().getPrice().multiply(new BigDecimal(pp.getQuantity())));
        }
        return subTotal;
    }

    public static void addCartToModel(Model model, ShoppingCart cart) {
        if (cart.getPurchase() != null) {
            model.addAttribute("cart", cart);
            BigDecimal subTotal = computeSubtotal(cart.getPurchase());
            model.addAttribute("subTotal", subTotal);
        }
    }
}
