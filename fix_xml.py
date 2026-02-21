import os

def clean_xml_files(directory):
    print(f"Nettoyage des fichiers XML dans : {directory}")
    count = 0
    
    for root, dirs, files in os.walk(directory):
        for file in files:
            if file.endswith(".xml"):
                path = os.path.join(root, file)
                try:
                    with open(path, "r", encoding="utf-8") as f:
                        content = f.read()
                    
                    # Vérifie si le fichier commence par un espace ou une ligne vide
                    if content and not content.startswith("<"):
                        # Enlève les espaces/lignes vides au début (lstrip)
                        cleaned_content = content.lstrip()
                        
                        # Si le contenu a changé, on réécrit
                        if content != cleaned_content:
                            with open(path, "w", encoding="utf-8") as f:
                                f.write(cleaned_content)
                            print(f"🔧 Réparé : {file}")
                            count += 1
                except Exception as e:
                    print(f"Erreur sur {file}: {e}")

    print(f"Terminé ! {count} fichiers XML corrigés.")

# Lance le nettoyage dans le dossier courant
clean_xml_files(".")