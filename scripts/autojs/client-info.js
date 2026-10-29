JSON.stringify({
    where: AutoJS.where(),
    divinitymc: AutoJS.modVersion("divinitymc"),
    autojs: AutoJS.modVersion("autojs"),
    rhino: AutoJS.modVersion("rhino"),
    ui: JSON.parse(AutoJS.ui())
});
