The line icons of the menus (src/main/java/com/iceymod/screen/Icons.java)

They are glyphs of the Lucide icon set (https://lucide.dev, ISC licence), shipped as a font that holds
only the icons the mod uses: src/main/resources/assets/iceymod/font/lucide.ttf (about 40 kB instead of 1 MB).

To add an icon:
  1. Get font/lucide.ttf and font/lucide.css from the npm package "lucide-static" (the version the font
     was cut from is in lucide-LICENSE.txt's folder history; 1.83.0 at the time of writing).
  2. Look the icon's code up in lucide.css (".icon-<name>::before { content: "\eXXX" }").
  3. Cut the font down to the codes in use plus the new one:
       pip install --target <dir> fonttools
       PYTHONPATH=<dir> python3 -m fontTools.subset lucide.ttf --unicodes="U+e038,U+e151,..." \
           --output-file=lucide.ttf --no-layout-closure --layout-features='' --name-IDs='*'
  4. Add the code to Icons.java.
