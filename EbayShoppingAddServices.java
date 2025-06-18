package week16;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.poi.xssf.usermodel.*;
import org.openqa.selenium.*;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.*;
import org.testng.Assert;
import org.testng.annotations.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;

public class EbayShoppingAddServices {
    Logger logger = LogManager.getLogger(EbayShoppingAddServices.class);
    WebDriver driver;
    WebDriverWait wait;

    String driverType = "webdriver.gecko.driver";
    String driverPath = "C:\\BrowserDrivers\\geckodriver-v0.33.0\\geckodriver.exe";
    String xlsxFilePath = "C:\\Users\\ekta9\\Downloads\\SFBU_Spring2025\\Testing\\Week 16\\CS522_2025_MyProject_150337_EktaAwasthi\\ShoppingFile1.xlsx";
    String screenshotPath = "C:\\Users\\ekta9\\Downloads\\SFBU_Spring2025\\Testing\\Week 16\\CS522_2025_MyProject_150337_EktaAwasthi\\Screenshots";
    String sheetName = "Sheet1";
    String url = "https://www.ebay.com";

    @BeforeTest
    public void setup() {
        System.setProperty(driverType, driverPath);

        // Disable pop-ups and notifications by using FirefoxOptions
        FirefoxOptions options = new FirefoxOptions();
        options.addPreference("dom.popup_allowed_events", "change click dblclick keydown keyup mousedown mouseup");
        options.addPreference("notifications.enabled", false); // Disable notifications

        driver = new FirefoxDriver(options);
        driver.manage().window().maximize();
        driver.manage().deleteAllCookies();  // Clears all cookies
        driver.get(url);
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        // Set up logging configuration
        Configurator.initialize(new DefaultConfiguration());
        Configurator.setRootLevel(Level.INFO);
        logger.info("Launched eBay website.");
    }

    @Test
    public void ebayShoppingTest() throws IOException {
        FileInputStream file = new FileInputStream(xlsxFilePath);
        XSSFWorkbook workbook = new XSSFWorkbook(file);
        XSSFSheet sheet = workbook.getSheet(sheetName);
        int noOfRows = sheet.getLastRowNum();
        logger.info("Excel rows found: " + noOfRows);

        for (int row = 1; row <= noOfRows; row++) {
            XSSFRow currentRow = sheet.getRow(row);
            String product = currentRow.getCell(0).getStringCellValue();
            String color = currentRow.getCell(1).getStringCellValue();
            String quantity = String.valueOf((int) currentRow.getCell(2).getNumericCellValue());
            String expectedPrice = String.valueOf((double) currentRow.getCell(3).getNumericCellValue());

            logger.info("Searching for product: " + product);
            try {
                takeSnapShot(driver, screenshotPath + "\\search_" + "image1.png");
            } catch (Exception e) {
                logger.error("Screenshot failed for image1 ");
            }
            searchProduct(product);

            // Close any pop-ups after the search
            closeGooglePopups();

            String safeProductName = sanitizeFileName(product);
            logger.info("The product name is:" + safeProductName);
            try {
                takeSnapShot(driver, screenshotPath + "\\search_" + safeProductName + ".png");
            } catch (Exception e) {
                logger.error("Screenshot failed: " + product);
            }

            if (selectFirstValidProduct()) {
                try {
                    // Wait for and retrieve the product title
                    WebElement productNameElement = wait.until(ExpectedConditions.visibilityOfElementLocated(
                            By.cssSelector("h1.x-item-title__mainTitle span")));
                    String expectedProductName = productNameElement.getText().trim();

                    logger.info("Expected Product Name: " + expectedProductName);
                    logger.info("Expected Product Price: " + expectedPrice);
                    logger.info("Expected Product Quantity: " + quantity);

                    takeSnapShot(driver, screenshotPath + "\\details_" + "quantity" + ".png");
                    customizeProductOptions(driver, quantity);

                    takeSnapShot(driver, screenshotPath + "\\details_" + "quantityChanged" + ".png");
                    // Click on Add to cart button
                    addToCart(driver);

                    takeSnapShot(driver, screenshotPath + "\\details_" + "addToCart" + ".png");

                    logger.info("Calling handleAdditionalServiceAndViewCart with name: '" + expectedProductName + "', price: '" + expectedPrice + "'");
                    handleAdditionalServiceAndViewCart(expectedProductName, quantity, expectedPrice);
                    takeSnapShot(driver, screenshotPath + "\\details_" + "viewCart" + ".png");
                } catch (Throwable t) {
                    logger.error("Failed to take screenshot" + product, t);
                }
            }
        }
        workbook.close();
    }

    private String sanitizeFileName(String product) {
        return product.replaceAll("[^a-zA-Z0-9\\-_.]", "_");
    }

    public void searchProduct(String product) {
        WebElement searchBox = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("gh-ac")));
        searchBox.clear();
        searchBox.sendKeys(product);
        searchBox.sendKeys(Keys.RETURN);
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("ul.srp-results")));
    }

    public void closeGooglePopups() {
        try {
            // Handle Google cookie consent pop-ups
            WebElement acceptCookiesButton = driver.findElement(By.xpath("//button[contains(text(), 'Accept') or contains(text(), 'I agree')]"));
            if (acceptCookiesButton.isDisplayed()) {
                acceptCookiesButton.click();
                logger.info("Accepted cookie consent pop-up.");
            }
        } catch (NoSuchElementException e) {
            logger.info("No cookie consent pop-up found.");
        }

        try {
            // Handle Google sign-in or verification pop-ups (if any)
            WebElement cancelSignInButton = driver.findElement(By.xpath("//button[contains(text(), 'Cancel') or contains(text(), 'Not now')]"));
            if (cancelSignInButton.isDisplayed()) {
                cancelSignInButton.click();
                logger.info("Closed Google sign-in pop-up.");
            }
        } catch (NoSuchElementException e) {
            logger.info("No Google sign-in pop-up found.");
        }

        try {
            // Handle "Sign in with Google" prompt (if any)
            WebElement signInButton = driver.findElement(By.xpath("//button[contains(text(), 'Sign in')]"));
            if (signInButton.isDisplayed()) {
                logger.info("Sign-in prompt appeared, dismissing.");
                signInButton.click(); // You can modify this to 'Not Now' or 'Cancel' depending on your website's behavior
            }
        } catch (NoSuchElementException e) {
            logger.info("No 'Sign in' pop-up found.");
        }
    }

    public boolean selectFirstValidProduct() {
        try {
            List<WebElement> results = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector("ul.srp-results > li.s-item")));
            if (results.isEmpty()) return false;

            String originalWindow = driver.getWindowHandle();

            for (WebElement item : results) {
                if (item.getText().contains("Buy It Now")) {
                    WebElement link = item.findElement(By.cssSelector("a.s-item__link"));
                    link.click();

                    // Handle possible new tab or window
                    for (String windowHandle : driver.getWindowHandles()) {
                        if (!windowHandle.equals(originalWindow)) {
                            driver.switchTo().window(windowHandle);
                            logger.info("Switched to new product tab/window.");
                            break;
                        }
                    }

                    // Wait for any valid element on product page
                    wait.until(ExpectedConditions.or(
                            ExpectedConditions.visibilityOfElementLocated(By.cssSelector("h1.x-item-title__mainTitle")),
                            ExpectedConditions.presenceOfElementLocated(By.id("atcBtn_btn_1"))
                    ));

                    logger.info("Product page loaded and required elements found.");
                    return true;
                }
            }

            logger.warn("No 'Buy It Now' listings found.");
            return false;
        } catch (Exception e) {
            logger.error("Failed to open or detect product page properly.", e);
            return false;
        }
    }

    public void customizeProductOptions(WebDriver driver, String quantity) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

        // Set quantity
        try {
            if (quantity != null && !quantity.trim().isEmpty()) {
                int qty = Integer.parseInt(quantity.trim());
                if (qty > 1) {
                    WebElement quantityBox = wait.until(ExpectedConditions.elementToBeClickable(By.name("quantity")));
                    quantityBox.clear();
                    quantityBox.sendKeys(String.valueOf(quantity));
                    logger.info("Set quantity to: " + quantity);
                } else {
                    logger.info("Quantity is 1 or less, using default.");
                }
            }
        } catch (Exception e) {
            logger.warn("Could not set quantity. Default (1) will be used.", e);
        }
    }


    public void addToCart(WebDriver driver) {
        Configurator.initialize(new DefaultConfiguration());
        Configurator.setRootLevel(Level.INFO);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        try {
            // Wait for the overlay (loading spinner) to disappear if present
            List<WebElement> overlays = driver.findElements(By.cssSelector(".x-atc-action.overlay-placeholder.loading"));
            if (!overlays.isEmpty()) {
                logger.info("Overlay is present, waiting for it to disappear...");
                wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".x-atc-action.overlay-placeholder.loading")));
            }

            // Wait until the Add to Cart button is clickable
            WebElement addToCartBtn = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("#atcBtn_btn_1")));

            // Scroll the button into view just in case
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", addToCartBtn);

            // Click the Add to Cart button
            addToCartBtn.click();
            logger.info("Clicked on Add to Cart button successfully.");
        } catch (TimeoutException e) {
            logger.error("Timed out waiting for Add to Cart button or overlay to disappear.", e);
        } catch (ElementClickInterceptedException e) {
            logger.warn("Element was not clickable initially, retrying after a short delay...");
            try {
                WebElement retryBtn = wait.until(
                        ExpectedConditions.elementToBeClickable(By.cssSelector("#atcBtn_btn_1"))
                );
                retryBtn.click();
                logger.info("Clicked on Add to Cart button on retry.");
            } catch (TimeoutException te) {
                logger.error("Retry Add to Cart button not clickable within timeout.", te);
            }
        } catch (Exception e) {
            logger.error("Failed to add product to cart.", e);
        }
    }

    public void handleAdditionalServiceAndViewCart(String expectedProductName, String expectedQuantity, String expectedPrice) throws InterruptedException {
        logger.info("Checking for 'Additional Service' or 'Added to Cart' dialog...");

        if (expectedProductName == null || expectedProductName.trim().isEmpty()) {
            logger.warn("Expected Product Name is empty! This may affect validation.");
        }
        if (expectedPrice == null || expectedPrice.trim().isEmpty()) {
            logger.warn("Expected Product Price is empty! This may affect validation.");
        }

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(40)); // Increased wait time
        boolean cartReached = false;

        try {
            logger.info("Waiting for 'Additional Service' modal to appear...");

            WebElement additionalServiceModal = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("#vas-container-interstitial-layer-d .lightbox-dialog__window")));

            try {
                takeSnapShot(driver, screenshotPath + "\\search_" + "additional_service.png");
            } catch (Exception e) {
                logger.warn("Screenshot failed: ", e);
            }

            logger.info("'Additional Service' modal is visible. Attempting to click 'Proceed'...");

            try {
                WebElement proceedButton = wait.until(ExpectedConditions.elementToBeClickable(
                        By.cssSelector("button.btn--primary")));
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", proceedButton);
                proceedButton.click();
                logger.info("'Proceed' button clicked successfully.");
            } catch (TimeoutException e) {
                logger.warn("Proceed button not found normally. Trying Shadow DOM click as fallback...");

                Object result = ((JavascriptExecutor) driver).executeScript(
                        "const shadowHost = document.querySelector('#vas-interstitial-target-d');" +
                                "if (shadowHost && shadowHost.shadowRoot) {" +
                                "  const btn = shadowHost.shadowRoot.querySelector('#vas-spoke-container button');" +
                                "  if (btn) { btn.click(); return 'clicked'; } else { return 'not found'; }" +
                                "} else { return 'shadow host missing'; }");
                logger.info("Shadow DOM click result: " + result);
            }

            // Wait for cart page to load
            logger.info("Waiting for Shopping Cart page...");
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("h1[data-test-id='main-title']")));
            cartReached = true;
            logger.info("Shopping cart page reached after 'Additional Service' modal.");

        } catch (TimeoutException e) {
            logger.warn("'Additional Service' modal not found. ", e);
        }

        if (cartReached) {
            try {
                logger.info("Validating product details in cart...");

                WebElement titleElement = driver.findElement(By.cssSelector("a[data-test-id='cart-item-link']"));
                String actualProductName = titleElement.getText().trim();
                logger.info("Cart Product Title: " + actualProductName);

                try {
                    Assert.assertTrue(actualProductName.contains(expectedProductName),
                            "Product name mismatch! Expected to contain: " + expectedProductName + ", Found: " + actualProductName);
                    logger.info("Product name matches.");
                } catch (AssertionError ae) {
                    logger.error("Product name assertion failed.", ae);
                    throw ae;
                }

                WebElement priceElement = driver.findElement(By.cssSelector("div.item-price span span span"));
                String actualPrice = priceElement.getText().trim().replaceAll("[^\\d.]", "");
                logger.info("Cart Product Price: " + actualPrice);

                try {
                    Assert.assertEquals(actualPrice, expectedPrice,
                            "Price mismatch! Expected: " + expectedPrice + ", Found: " + actualPrice);
                    logger.info("Product price matches.");
                } catch (AssertionError ae) {
                    logger.error("Product price assertion failed.", ae);
                    throw ae;
                }

                WebElement qtyDropdown = driver.findElement(By.cssSelector("select[data-test-id='qty-dropdown']"));
                Select actualQuantity = new Select(qtyDropdown);
                String selectedQty = actualQuantity.getFirstSelectedOption().getText().trim();
                logger.info("Selected quantity in cart: " + selectedQty);
                try {
                    Assert.assertEquals(selectedQty, expectedQuantity,
                            "Quantity mismatch! Expected: " + expectedQuantity + ", Found: " + actualQuantity);
                    logger.info("Product quantity matches.");
                } catch (AssertionError ae) {
                    logger.error("Product quantity assertion failed.", ae);
                    throw ae;
                }


            } catch (Exception e) {
                logger.error("Error validating cart contents.", e);
            }
        } else {
            logger.error("Could not reach Shopping Cart. Validation skipped.");
        }
    }


    public void takeSnapShot(WebDriver webdriver, String fileWithPath) throws Exception {
        TakesScreenshot scrShot = ((TakesScreenshot) webdriver);
        File srcFile = scrShot.getScreenshotAs(OutputType.FILE);
        File destFile = new File(fileWithPath);
        FileUtils.copyFile(srcFile, destFile);
    }

    @AfterTest
    public void tearDown() {
        driver.quit();
        logger.info("Browser closed.");
    }
}
