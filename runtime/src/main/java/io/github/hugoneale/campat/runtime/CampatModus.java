package io.github.hugoneale.campat.runtime;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(CampatModus.MOD_ID)
public final class CampatModus {
    public static final String MOD_ID = "campat_modus";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CampatModus(FMLJavaModLoadingContext context) {
        LOGGER.info("Campat Modus runtime loaded");
    }
}
