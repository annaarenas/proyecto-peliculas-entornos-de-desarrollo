import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        // Configurar WebDriver
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Conexión a la base de datos
        String urlDB = "jdbc:mysql://localhost:3306/filmaffinity_db";
        String usuario = "root";
        String contrasena = "1234";

        try (Connection conexion = DriverManager.getConnection(urlDB, usuario, contrasena)) {
            System.out.println("Conexión exitosa a la base de datos");

            // Ir a la página principal
            driver.get("https://www.filmaffinity.com/es/topgen.php");

            // Cargar más películas hasta hacer 50 clics
            int clics = 0;
            while (clics < 120) {
                try {
                    WebElement loadMoreButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("load-more-bt")));
                    if (loadMoreButton.isDisplayed()) {
                        System.out.println("Clic en 'Ver más resultados' #" + (clics + 1));
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", loadMoreButton);
                        clics++;
                        Thread.sleep(3000);
                    } else {
                        System.out.println("Botón 'Ver más resultados' no visible. Terminando.");
                        break;
                    }
                } catch (TimeoutException | InterruptedException e) {
                    System.out.println("No hay más películas para cargar o el botón no está disponible.");
                    break;
                }
            }

            // Buscar películas
            List<WebElement> peliculas = driver.findElements(By.cssSelector(".movie-card .mc-title a"));
            System.out.println("Se encontraron " + peliculas.size() + " películas.");

            for (WebElement pelicula : peliculas) {
                try {
                    // Obtener la URL de la película
                    String urlPelicula = pelicula.getAttribute("href");
                    System.out.println("Entrando en: " + urlPelicula);

                    // Abrir una nueva pestaña
                    ((JavascriptExecutor) driver).executeScript("window.open(arguments[0], '_blank');", urlPelicula);
                    List<String> tabs = new ArrayList<>(driver.getWindowHandles());
                    driver.switchTo().window(tabs.get(1));
                    Thread.sleep(2000);

                    // Extraer información de la película
                    String titulo = obtenerTextoSeguro(driver, wait, "//dt[contains(text(),'Título original')]/following-sibling::dd[1]");
                    String año = obtenerTextoSeguro(driver, wait, "//dd[@itemprop='datePublished']");
                    String puntuacion = obtenerTextoSeguro(driver, wait, "//div[@id='movie-rat-avg']");
                    String votos = obtenerTextoSeguro(driver, wait, "//span[@itemprop='ratingCount']");
                    String pais = obtenerTextoSeguro(driver, wait, "//dt[contains(text(),'País')]/following-sibling::dd[1]");
                    String duracion = obtenerTextoSeguro(driver, wait, "//dd[@itemprop='duration']");
                    String genero = obtenerTextoSeguro(driver, wait, "//span[@itemprop='genre']");

                    System.out.println("Pelicula: " + titulo + " | Año: " + año + " | Puntuación: " + puntuacion + " | Votos: " + votos + " | País: " + pais + " | Duración: " + duracion + " | Género: " + genero);

                    // Validar y limpiar los datos antes de guardarlos
                    if (!año.matches("\\d{4}")) año = "0";
                    puntuacion = limpiarPuntuacion(puntuacion);
                    votos = limpiarVotos(votos);

                    // Verificar si la película ya existe antes de insertarla
                    if (!existePelicula(conexion, titulo, año)) {
                        guardarEnBaseDeDatos(conexion, titulo, año, puntuacion, votos, pais, duracion, genero);
                    } else {
                        System.out.println("La película '" + titulo + "' ya existe en la base de datos. No se insertará.");
                    }

                    // Cerrar pestaña actual y volver a la principal
                    driver.close();
                    driver.switchTo().window(tabs.get(0));
                } catch (Exception e) {
                    System.out.println("Error al extraer datos de una película: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            System.out.println("Error al conectar con la base de datos: " + e.getMessage());
        } finally {
            driver.quit();
        }
    }

    public static boolean existePelicula(Connection conn, String titulo, String año) {
        String sql = "SELECT COUNT(*) FROM peliculas WHERE titulo = ? AND año = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, titulo);
            pstmt.setInt(2, Integer.parseInt(año));

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.out.println("Error al verificar existencia en la base de datos: " + e.getMessage());
        }
        return false;
    }

    public static void guardarEnBaseDeDatos(Connection conn, String titulo, String año, String puntuacion, String votos, String pais, String duracion, String genero) {
        String sql = "INSERT INTO peliculas (titulo, año, puntuacion, votos, pais, duracion, genero) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            System.out.println("Insertando: Titulo: " + titulo + ", Año: " + año + ", Puntuacion: " + puntuacion + ", Votos: " + votos + ", País: " + pais + ", Duración: " + duracion + ", Género: " + genero);

            pstmt.setString(1, titulo);
            pstmt.setInt(2, Integer.parseInt(año));
            pstmt.setBigDecimal(3, new BigDecimal(puntuacion));
            pstmt.setInt(4, Integer.parseInt(votos));
            pstmt.setString(5, pais);
            pstmt.setString(6, duracion);
            pstmt.setString(7, genero);

            int rowsAffected = pstmt.executeUpdate();
            System.out.println("Filas afectadas: " + rowsAffected);
        } catch (NumberFormatException e) {
            System.out.println("Error al convertir los datos: " + e.getMessage());
        } catch (SQLException e) {
            System.out.println("Error al guardar en la base de datos: " + e.getMessage());
        }
    }

    private static String obtenerTextoSeguro(WebDriver driver, WebDriverWait wait, String xpath) {
        try {
            WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(xpath)));
            return element.getText().replace("\u00a0", " ").trim();
        } catch (Exception e) {
            return "0";
        }
    }

    private static String limpiarPuntuacion(String puntuacion) {
        try {
            puntuacion = puntuacion.replace(",", ".");
            return puntuacion.matches("\\d+(\\.\\d+)?") ? puntuacion : "0";
        } catch (Exception e) {
            return "0";
        }
    }

    private static String limpiarVotos(String votos) {
        return votos.replaceAll("[^\\d]", "");
    }
}
