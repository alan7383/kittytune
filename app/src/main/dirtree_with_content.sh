#!/usr/bin/env bash

# 1. Configuration
ROOT="$(pwd)"
SCRIPT_NAME=$(basename "$0")
OUT="dump_project_context.txt"

# Dossiers à ignorer strictement (optimisation -prune)
# On exclut aussi .hg, .svn, .idea, etc.
EXCLUDE_DIRS=".git .gradle build out .idea .android node_modules captutes .cxx"

# Extensions autorisées (regex)
INCLUDE_EXT="kt|java|xml|gradle|properties|pro|txt|md|json|toml|kts"

# Taille max par fichier (100 KB)
MAX_SIZE=102400

# Construction de la chaine pour 'tree' (format: dossier1|dossier2|...)
TREE_EXCLUDE=$(echo $EXCLUDE_DIRS | tr ' ' '|')

echo "Analyse en cours... Résultat dans : $OUT"

{
  # 2. Chemin absolu
  echo "================================================================================"
  echo "PROJET : $ROOT"
  echo "DATE   : $(date)"
  echo "================================================================================"
  echo

  # 3. Résumé (Comptage rapide)
  # On utilise find avec prune pour compter sans descendre dans les dossiers lourds
  echo "RÉSUMÉ :"
  echo "----------------------------------------"
  
  # Construction de la commande prune pour find
  PRUNE_CMD=""
  for d in $EXCLUDE_DIRS; do
    PRUNE_CMD="$PRUNE_CMD -name \"$d\" -prune -o"
  done
  
  # Comptage des fichiers pertinents
  FILE_COUNT=$(eval find . $PRUNE_CMD -type f -regextype posix-extended -regex "'.*\.($INCLUDE_EXT)'" -print | wc -l)
  DIR_COUNT=$(eval find . $PRUNE_CMD -type d -print | wc -l)
  
  echo "Dossiers scannés (approx) : $DIR_COUNT"
  echo "Fichiers texte trouvés    : $FILE_COUNT"
  echo

  # 4. Arborescence (Tree)
  echo "ARBORESCENCE :"
  echo "----------------------------------------"
  if command -v tree &> /dev/null; then
      tree -a --dirsfirst -I "$TREE_EXCLUDE|$OUT|$SCRIPT_NAME" --noreport
  else
      echo "Commande 'tree' non trouvée. Utilisation de find basique :"
      eval find . $PRUNE_CMD -print | sed -e "s/[^-][^\/]*\// |/g" -e "s/|\([^ ]\)/|-\1/"
  fi
  echo

  # 5. Contenu des fichiers
  echo "CONTENU DES FICHIERS :"
  echo "----------------------------------------"
  
  # La commande FIND sécurisée
  # -prune : empêche d'entrer dans les dossiers build/git
  # ! -name "$OUT" : EMPÊCHE LA BOUCLE INFINIE DE 230 GO
  
  eval find . \
    $PRUNE_CMD \
    -type f \
    -size -"${MAX_SIZE}c" \
    -regextype posix-extended \
    -regex "'.*\.($INCLUDE_EXT)'" \
    ! -name "\"$OUT\"" \
    ! -name "\"$SCRIPT_NAME\"" \
    -print \
  | sort \
  | while read -r file; do
      echo "================================================================================"
      echo "FICHIER : $file"
      echo "================================================================================"
      # Ajoute une indentation de 4 espaces pour la lisibilité
      sed 's/^/    /' "$file"
      echo
      echo
    done

} > "$OUT"

echo "✔ Analyse terminée avec succès."
echo "✔ Fichier généré : $ROOT/$OUT"